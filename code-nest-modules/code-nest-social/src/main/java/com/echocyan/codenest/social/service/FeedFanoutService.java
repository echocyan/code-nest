package com.echocyan.codenest.social.service;

/**
 * 推拉结合 Feed 的写入侧：发文时推送，关注、取关、删文时修正发件箱与收件箱。各操作重复执行结果不变，
 * 修正不到的残留由读 Feed 时过滤。与 {@code feed.mode} 无关，两档都维护，切换档位时不需要预热。
 */
public interface FeedFanoutService {

    /**
     * 写入作者的发件箱；作者不是大 V 时，再推送给收件箱仍存在的粉丝。
     */
    void push(long articleId, long authorId);

    /**
     * 从作者的发件箱中移除文章；粉丝收件箱里的不逐个移除。
     */
    void removeArticle(long articleId, long authorId);

    /**
     * 更新作者的粉丝数（用于识别大 V）；关注的是普通作者时，把他的发件箱并入读者已存在的收件箱。
     * 大 V 的文章在读取时拉取，不需要并入。
     */
    void mergeOnFollow(long followerId, long authorId);

    /**
     * 更新作者的粉丝数（用于识别大 V），并按作者的发件箱，从读者的收件箱中移除他的文章。
     */
    void removeOnUnfollow(long followerId, long authorId);

    /**
     * Redis 里的 Feed 数据丢失时（首次部署、Redis 数据丢失，或造数绕过事件直接写库）重建，完好时什么也不做：
     * <ul>
     *     <li>发件箱的重建完成标记不存在时，按全部已发布文章重建各作者的发件箱，完成后写入标记；</li>
     *     <li>识别大 V 用的粉丝数不存在时，按全部关注关系重建。</li>
     * </ul>
     * 收件箱不用重建，读取时会从发件箱懒重建。
     */
    void rebuildIfAbsent();
}
