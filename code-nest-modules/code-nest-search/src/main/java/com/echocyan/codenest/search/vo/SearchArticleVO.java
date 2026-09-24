package com.echocyan.codenest.search.vo;

import com.echocyan.codenest.article.api.ArticleBrief;
import com.echocyan.codenest.user.api.UserBrief;

/**
 * 搜索结果中的一篇文章。
 *
 * @param author           查询时从 user 模块补全，昵称总是最新的
 * @param titleHighlight   高亮后的标题；实现不支持高亮时为 null
 * @param contentHighlight 正文中命中关键词的高亮片段；实现不支持高亮时为 null
 */
public record SearchArticleVO(ArticleBrief article, UserBrief author, String titleHighlight,
                              String contentHighlight) {
}
