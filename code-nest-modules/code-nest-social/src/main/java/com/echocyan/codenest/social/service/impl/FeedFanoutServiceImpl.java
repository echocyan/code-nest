package com.echocyan.codenest.social.service.impl;

import com.echocyan.codenest.article.api.ArticleApi;
import com.echocyan.codenest.article.api.ArticleState;
import com.echocyan.codenest.social.service.FeedFanoutService;
import com.echocyan.codenest.social.service.FollowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
class FeedFanoutServiceImpl implements FeedFanoutService {

    /**
     * 推送时每页取出的粉丝数。
     */
    static final int FOLLOWER_PAGE = 1000;

    /**
     * 重建发件箱时每批读取的文章数。
     */
    private static final int REBUILD_BATCH = 1000;

    private final FeedBoxes feedBoxes;
    private final BigAuthors bigAuthors;
    private final FollowService followService;
    private final ArticleApi articleApi;

    /**
     * 先写发件箱再推送：读者在推送途中重建收件箱时，从发件箱里也能拿到这篇文章。
     */
    @Override
    public void push(long articleId, long authorId) {
        feedBoxes.addToOutbox(authorId, articleId);
        if (bigAuthors.isBig(authorId)) {
            return;
        }
        Long after = null;
        List<Long> followerIds;
        do {
            followerIds = followService.listFollowerIds(authorId, after, FOLLOWER_PAGE);
            if (!followerIds.isEmpty()) {
                feedBoxes.pushToInboxes(followerIds, articleId);
                after = followerIds.getLast();
            }
        } while (followerIds.size() == FOLLOWER_PAGE);
    }

    @Override
    public void removeArticle(long articleId, long authorId) {
        feedBoxes.removeFromOutbox(authorId, articleId);
    }

    @Override
    public void mergeOnFollow(long followerId, long authorId) {
        if (!bigAuthors.isBig(authorId)) {
            feedBoxes.mergeOutboxIntoInbox(followerId, authorId);
        }
    }

    @Override
    public void removeOnUnfollow(long followerId, long authorId) {
        feedBoxes.removeOutboxFromInbox(followerId, authorId);
    }

    /**
     * 按文章 ID 正序导入，发件箱写入时裁剪，最后留下的就是每个作者最新的文章。导入期间发布的文章照常写入，
     * 重复写入无害；导入期间删除的文章可能被写回，由读 Feed 时过滤。多个实例同时启动时各自导入一遍。
     */
    @Override
    public void rebuildOutboxesIfAbsent() {
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
