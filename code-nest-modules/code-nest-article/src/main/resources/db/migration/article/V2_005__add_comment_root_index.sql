-- 对账按评论统计回复数；已有的 idx_article_root_id 以 article_id 开头，用不上
ALTER TABLE comment
    ADD KEY idx_root_id (root_id);
