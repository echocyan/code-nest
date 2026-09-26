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
     * 关注普通作者时，把他的发件箱并入读者已存在的收件箱。大 V 的文章在读取时拉取，不需要并入。
     */
    void mergeOnFollow(long followerId, long authorId);

    /**
     * 按作者的发件箱，从读者的收件箱中移除他的文章。
     */
    void removeOnUnfollow(long followerId, long authorId);

    /**
     * 发件箱的重建完成标记不存在时（首次部署、Redis 数据丢失，或造数绕过发文事件直接写库），按全部已发布文章重建
     * 各作者的发件箱，完成后写入标记；标记存在时什么也不做。收件箱不用重建，读取时会从发件箱懒重建。
     */
    void rebuildOutboxesIfAbsent();
}
