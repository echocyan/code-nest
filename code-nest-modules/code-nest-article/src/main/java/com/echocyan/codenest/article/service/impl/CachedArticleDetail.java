package com.echocyan.codenest.article.service.impl;

import com.echocyan.codenest.article.entity.Article;
import com.echocyan.codenest.article.vo.CategoryVO;
import com.echocyan.codenest.article.vo.TagVO;

import java.util.List;

/**
 * 缓存中的文章详情：元数据、正文、分类与标签。作者信息和计数经各自的门面另行组装，不放进缓存，
 * 所以点赞、改昵称不会让它失效。
 */
record CachedArticleDetail(Article article, String content, CategoryVO category, List<TagVO> tags) {
}
