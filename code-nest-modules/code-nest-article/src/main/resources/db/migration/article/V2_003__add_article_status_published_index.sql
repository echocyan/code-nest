-- 不带筛选条件的最新文章列表（首页）只有 status 条件，已有的两个索引都以 author_id / category_id 开头，用不上
ALTER TABLE article
    ADD KEY idx_status_published (status, published_at);
