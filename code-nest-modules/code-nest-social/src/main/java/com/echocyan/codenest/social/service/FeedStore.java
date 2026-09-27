package com.echocyan.codenest.social.service;

import com.echocyan.codenest.article.api.ArticleApi;
import com.echocyan.codenest.article.api.ArticleState;
import com.echocyan.codenest.common.result.CursorResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 推拉结合的 Feed 存储，全部在 Redis 里：每个作者一个发件箱，每个读者一个收件箱。普通作者发文时推送到粉丝的收件箱；
 * 粉丝数不低于阈值的大 V 发文不推送，读取时从其发件箱拉取，避免写扩散。
 * <p>
 * 写入侧各操作重复执行结果不变，由 MQ 消费者在发文、删文、关注、取关后调用。收件箱与发件箱里可能残留已删除的文章、
 * 已取关作者的文章，{@link #read} 不过滤，由调用方按文章的最新状态过滤。
 * <p>
 * 收件箱至多 {@value FeedBoxes#INBOX_CAP} 条、发件箱至多 {@value FeedBoxes#OUTBOX_CAP} 条，Feed 只能往回翻这么多。
 * <p>
 * 作者升为大 V 时，已推送的文章留在粉丝的收件箱里，读取时与拉取的按 ID 去重；降为普通作者时，把他的发件箱并入全部粉丝
 * 已存在的收件箱，否则他当大 V 期间的文章既不在收件箱里、也不再被拉取。
 * <p>
 * 发件箱与识别大 V 用的粉丝数只在启动时（所有单例创建完后）按数据库重建，见 {@link #afterSingletonsInstantiated}；
 * 收件箱在读取时懒重建。
 */
@Slf4j
@Component
public class FeedStore implements SmartInitializingSingleton {

    /**
     * 推送时每页取出的粉丝数。
     */
    private static final int FOLLOWER_PAGE = 1000;

    /**
     * 重建发件箱时每批读取的文章数。
     */
    private static final int REBUILD_BATCH = 1000;

    private final FeedBoxes feedBoxes;
    private final BigAuthors bigAuthors;
    private final FollowService followService;
    private final ArticleApi articleApi;

    @Autowired
    public FeedStore(StringRedisTemplate redis, FollowService followService, ArticleApi articleApi,
                     @Value("${feed.big-author-threshold}") long bigAuthorThreshold) {
        this(redis, followService, articleApi, "feed", bigAuthorThreshold);
    }

    /**
     * @param keyPrefix          全部 Redis key 的前缀，应用里用 {@code feed}
     * @param bigAuthorThreshold 粉丝数不低于它的作者是大 V
     */
    public FeedStore(StringRedisTemplate redis, FollowService followService, ArticleApi articleApi, String keyPrefix,
                     long bigAuthorThreshold) {
        this.feedBoxes = new FeedBoxes(redis, keyPrefix);
        this.bigAuthors = new BigAuthors(redis, followService, keyPrefix, bigAuthorThreshold);
        this.followService = followService;
        this.articleApi = articleApi;
    }

    /**
     * 写入作者的发件箱；作者不是大 V 时，再推送给收件箱仍存在的粉丝。
     * <p>
     * 先写发件箱再推送：读者在推送途中重建收件箱时，从发件箱里也能拿到这篇文章。
     */
    public void addArticle(long articleId, long authorId) {
        feedBoxes.addToOutbox(authorId, articleId);
        if (!bigAuthors.isBig(authorId)) {
            forEachFollowerPage(authorId, followerIds -> feedBoxes.pushToInboxes(followerIds, articleId));
        }
    }

    /**
     * 从作者的发件箱中移除文章；粉丝收件箱里的不逐个移除。
     */
    public void removeArticle(long articleId, long authorId) {
        feedBoxes.removeFromOutbox(authorId, articleId);
    }

    /**
     * 更新作者的粉丝数；关注的是普通作者时，把他的发件箱并入读者已存在的收件箱。大 V 的文章在读取时拉取，不需要并入。
     */
    public void follow(long followerId, long authorId) {
        refreshFollowers(authorId);
        if (!bigAuthors.isBig(authorId)) {
            feedBoxes.mergeOutboxIntoInbox(followerId, authorId);
        }
    }

    /**
     * 更新作者的粉丝数，并按作者的发件箱，从读者的收件箱中移除他的文章；更早的、已不在发件箱里的留在收件箱中。
     */
    public void unfollow(long followerId, long authorId) {
        refreshFollowers(authorId);
        feedBoxes.removeOutboxFromInbox(followerId, authorId);
    }

    /**
     * 读取一页：收件箱与各大 V 的发件箱合并，按文章 ID 去重、倒序截取。收件箱不存在（读者 7 天没来过）时先从普通作者的
     * 发件箱重建，存在时续期。
     *
     * @param authorIds 读者关注的全部作者，不为空
     * @param cursor    上一页的 nextCursor，第一页为 null
     * @return 文章 ID，可能含已删除的文章、已取关作者的文章；nextCursor 是这一页最后一个 ID
     */
    public CursorResult<Long> read(long userId, List<Long> authorIds, Long cursor, int size) {
        Set<Long> big = bigAuthors.among(authorIds);
        feedBoxes.renewOrRebuildInbox(userId, authorIds.stream().filter(id -> !big.contains(id)).toList());
        // 多取一条，只用来判断是否还有下一页
        List<Long> ids = feedBoxes.readBefore(userId, big, cursor, size + 1).stream()
                .distinct()
                .sorted(Comparator.reverseOrder())
                .limit(size + 1)
                .toList();
        return CursorResult.ofOverfetched(ids, size, Function.identity());
    }

    /**
     * 按关注表更新作者的粉丝数；作者因此降为普通作者时，把他的发件箱并入全部粉丝已存在的收件箱。
     */
    private void refreshFollowers(long authorId) {
        if (bigAuthors.refresh(authorId)) {
            forEachFollowerPage(authorId, followerIds -> feedBoxes.mergeOutboxIntoInboxes(followerIds, authorId));
        }
    }

    /**
     * 按粉丝 ID 升序，每页 {@value #FOLLOWER_PAGE} 个遍历作者的全部粉丝。
     */
    private void forEachFollowerPage(long authorId, Consumer<List<Long>> handler) {
        Long after = null;
        List<Long> followerIds;
        do {
            followerIds = followService.listFollowerIds(authorId, after, FOLLOWER_PAGE);
            if (!followerIds.isEmpty()) {
                handler.accept(followerIds);
                after = followerIds.getLast();
            }
        } while (followerIds.size() == FOLLOWER_PAGE);
    }

    /**
     * Redis 里的 Feed 数据丢失时（首次部署、Redis 数据丢失，或造数绕过事件直接写库）重建，完好时什么也不做：
     * <ul>
     *     <li>发件箱的重建完成标记不存在时，按全部已发布文章重建各作者的发件箱；</li>
     *     <li>识别大 V 用的粉丝数的重建完成标记不存在时，按全部关注关系重建。</li>
     * </ul>
     * 两者都在全部导入后才写标记，中途失败时下次启动重来。
     */
    @Override
    public void afterSingletonsInstantiated() {
        rebuildOutboxesIfAbsent();
        bigAuthors.rebuildIfAbsent();
    }

    /**
     * 按文章 ID 正序导入，发件箱写入时裁剪，最后留下的就是每个作者最新的文章。导入期间发布的文章照常写入，
     * 重复写入无害；导入期间删除的文章可能被写回，由读 Feed 时过滤。多个实例同时启动时各自导入一遍。
     */
    private void rebuildOutboxesIfAbsent() {
        if (feedBoxes.outboxesReady()) {
            return;
        }
        long imported = 0;
        List<ArticleState> articles = articleApi.listPublishedStates(null, REBUILD_BATCH);
        while (!articles.isEmpty()) {
            feedBoxes.addToOutboxes(articles);
            imported += articles.size();
            articles = articleApi.listPublishedStates(articles.getLast().id(), REBUILD_BATCH);
        }
        feedBoxes.markOutboxesReady();
        log.info("Rebuilt feed outboxes from {} published articles", imported);
    }
}
