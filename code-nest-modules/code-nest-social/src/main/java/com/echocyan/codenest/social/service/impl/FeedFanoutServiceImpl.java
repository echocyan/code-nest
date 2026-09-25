package com.echocyan.codenest.social.service.impl;

import com.echocyan.codenest.social.service.FeedFanoutService;
import com.echocyan.codenest.social.service.FollowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
class FeedFanoutServiceImpl implements FeedFanoutService {

    /**
     * 推送时每页取出的粉丝数。
     */
    static final int FOLLOWER_PAGE = 1000;

    private final FeedBoxes feedBoxes;
    private final BigAuthors bigAuthors;
    private final FollowService followService;

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
}
