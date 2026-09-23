package com.echocyan.codenest.architecture.fixture.boundary.user.service;

import com.echocyan.codenest.architecture.fixture.boundary.article.api.ArticleApi;

/** 经 api 包调用，但 user → article 不在 DAG 中。 */
public class ReverseEdge {
    ArticleApi articleApi;
}
