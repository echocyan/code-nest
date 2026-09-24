package com.echocyan.codenest.article;

import com.echocyan.codenest.common.exception.ErrorCode;

/**
 * article 模块错误码，号段 2xxxx。
 */
public enum ArticleErrorCode implements ErrorCode {

    ARTICLE_NOT_FOUND(20001, "文章不存在", 404),
    VERSION_CONFLICT(20002, "文章已在别处修改，请刷新后重试", 409),
    CATEGORY_NOT_FOUND(20003, "分类不存在", 400),
    TAG_NOT_FOUND(20004, "标签不存在", 400),
    COMMENT_NOT_FOUND(20005, "评论不存在", 404),
    REPLY_TO_USER_INVALID(20006, "只能回复该评论下参与讨论的用户", 400);

    private final int code;
    private final String message;
    private final int httpStatus;

    ArticleErrorCode(int code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override
    public int code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public int httpStatus() {
        return httpStatus;
    }
}
