package com.echocyan.codenest.search.vo;

import com.echocyan.codenest.article.api.ArticleItem;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

/**
 * 搜索结果中的一篇文章：文章列表项的全部字段，外加高亮。
 *
 * @param titleHighlight   高亮后的标题，标题未命中时为 null
 * @param contentHighlight 正文中命中关键词的高亮片段，正文未命中时为 null
 */
public record SearchArticleVO(@JsonUnwrapped ArticleItem article, String titleHighlight, String contentHighlight) {
}
