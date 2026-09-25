# 领域与数据模型

Type: grilling
Status: resolved
Blocked by:

## Question

用户、文章（含标签/分类、Markdown 正文存储）、评论与回复、点赞、收藏、关注、通知各自的实体、字段、表结构与索引是什么？评论与回复是一张表还是两张？软删除策略？计数字段（点赞数、粉丝数等）放在主表还是独立计数表——留给计数系统票细化，但此处需确定预留方式。

## Answer

### 业务规则
1. **分类与标签**：每篇文章恰好选一个分类，最多打 5 个标签。分类和标签都由系统预置，用 Flyway 写入种子数据，用户不能新建。
2. **文章状态**：只有草稿（0）和已发布（1）两种，不设审核状态。只有已发布的文章会进入关注 Feed、搜索和热榜。发布后可以编辑；编辑后要更新搜索索引和缓存，但不会重新推送 Feed。
3. **正文与图片**：Markdown 原文单独存在 `article_content` 表里，与文章主表垂直拆分。摘要由作者填写，没填就截取正文前 N 个字。Markdown 渲染由前端负责。封面和头像只存 URL；图片上传不做。
4. **评论与回复**：共用一张表。`root_id = 0` 的是评论，否则是挂在该评论下的回复。同一评论下的回复平铺展示，用 `reply_to_user_id` 标明"回复 @某人"。
5. **删除**：文章、评论、回复用 `@TableLogic` 软删除。评论被删后，如果下面还有回复，显示"该评论已删除"，回复保留。点赞、收藏、关注取消时直接物理删除。不支持注销账号。
6. **点赞与收藏**：只有文章能点赞，评论不能点赞。收藏只有一个平铺列表，不分收藏夹。
7. **计数**：计数存在独立的计数表里，不作为主表的冗余字段。
   - 文章：点赞数、收藏数、评论数、浏览量。浏览量只统计总次数，不按访客去重。
   - 用户：粉丝数、关注数、文章数、获赞总数。
   - 评论：回复数。

   写入路径以及 Redis 与 MySQL 如何对账，由计数系统票决定。

### 模块归属与依赖
- `code-nest-counter` 模块负责所有计数表的读写。它不依赖任何业务模块，各业务模块通过 `CounterApi` 上报和读取计数。
- interaction 模块只负责点赞和收藏。"当前用户是否点赞或收藏过"由 interaction 单独提供批量查询接口，文章详情接口里不带这个状态。
- 模块依赖（全部依赖 framework 和 common，下面省略）：
  ```
  user → counter（用户主页展示计数）；counter → 无
  article → user, counter
  interaction → article, counter
  social → user, article, counter
  notification → user, article, interaction, social
  search → article, user
  ```

### 建表约定
- 不建物理外键。
- 主键用雪花 BIGINT；计数表例外，以业务 ID 作主键。
- 所有表都有 `created_at`、`updated_at`，由 MyBatis-Plus 自动填充；只有内容类表有 `deleted` 字段。
- 字符集 `utf8mb4`，引擎 InnoDB，时间字段用 `DATETIME`，时区 Asia/Shanghai。

### 表结构草案
每张表都有的 `created_at`、`updated_at` 下面省略不写。
```
-- user
user            id, username UK, password_hash, nickname, avatar_url, bio    （认证与鉴权票可能再加字段）
-- article
category        id, name UK, sort
tag             id, name UK
article         id, author_id, category_id, title, summary, cover_url, status, published_at, version, deleted
                （version：MyBatis-Plus @Version，每次编辑/发布/删除 +1，兼作编辑乐观锁与 ES 外部版本号，见[搜索与数据同步](08-search-sync.md)）
                IDX(author_id, status, published_at), IDX(category_id, status, published_at)
                IDX(status, published_at)（不带筛选的最新文章列表）
article_content article_id PK, content MEDIUMTEXT
article_tag     PK(article_id, tag_id), IDX(tag_id, article_id)
comment         id, article_id, user_id, root_id, reply_to_user_id, content VARCHAR(1000), deleted
                IDX(article_id, root_id, id), IDX(root_id)
-- counter
article_stat    article_id PK, like_count, favorite_count, comment_count, view_count
user_stat       user_id PK, follower_count, following_count, article_count, like_received_count
comment_stat    comment_id PK, reply_count
-- interaction
article_like    id, user_id, article_id, author_id（被点赞文章的作者，对账按它统计获赞数）
                UK(user_id, article_id), IDX(article_id), IDX(author_id)
favorite        id, user_id, article_id, UK(user_id, article_id), IDX(user_id, id), IDX(article_id)
-- social
follow          id, follower_id, author_id, UK(follower_id, author_id), IDX(author_id, follower_id)
                IDX(author_id, id), IDX(follower_id, id)
-- notification（由[通知模块](13-notification.md)定稿）
notification    id, recipient_id, actor_id, type, article_id, comment_id, dedup_key NULL, is_read
                UK(dedup_key), IDX(recipient_id, id), IDX(recipient_id, is_read)
```
这里没有列出的表：Feed 收件箱（在 Redis 里，由 Feed 票决定）、Outbox 本地消息表（由消息可靠性底座票决定）、搜索相关的表（由搜索票决定）。

跨模块取数不联表，只在各自表里存 ID，由服务层调用对方的 `XxxApi` 批量查询后组装（见 ADR-0001）。评论数由 article 模块上报，获赞总数由 interaction 模块上报。
