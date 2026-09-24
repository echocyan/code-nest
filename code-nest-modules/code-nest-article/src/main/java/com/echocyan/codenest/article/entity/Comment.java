package com.echocyan.codenest.article.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.echocyan.codenest.framework.mybatis.AuditableEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 评论或回复：{@code rootId = 0} 是评论，否则是挂在该评论下的回复。
 */
@Getter
@Setter
@TableName("comment")
public class Comment extends AuditableEntity {

    /** 评论的 rootId。 */
    public static final long NO_ROOT = 0L;

    private Long id;

    private Long articleId;

    private Long userId;

    private Long rootId;

    /** 回复 @某人；为 null 表示回复评论本身，评论恒为 null。 */
    private Long replyToUserId;

    private String content;

    @TableLogic
    private Integer deleted;

    public boolean isReply() {
        return rootId != NO_ROOT;
    }
}
