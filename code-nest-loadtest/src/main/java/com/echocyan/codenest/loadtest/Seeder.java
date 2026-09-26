package com.echocyan.codenest.loadtest;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.IntPredicate;

/**
 * 压测造数：用 JDBC 多行批量 insert 直接写 MySQL，固定随机种子，同样的空库每次造出同样结构的数据
 * （时间与 ID 相对运行时刻平移）。只写关系与内容，计数、ES、布隆过滤器、Feed 发件箱等派生数据走系统自带的重建路径，
 * 见 {@code seed.sh}。
 *
 * <p>用户按下标分段，用户名体现角色，造数账号的密码都是 {@value #PASSWORD}：
 * <ul>
 *     <li>{@code bigv_00}–{@code bigv_09}：大 V，各 {@value #BIG_V_FANS} 个粉丝、{@value #BIG_V_ARTICLES} 篇文章；</li>
 *     <li>{@code heavy_000}–{@code heavy_099}：重度用户，各关注 {@value #HEAVY_FOLLOWS} 人（10 个大 V 加普通作者），不写文章；</li>
 *     <li>{@code author_4999}：普通作者，恰好 {@value #AUTHOR_4999_FANS} 个粉丝，低于大 V 阈值；</li>
 *     <li>{@code user_00000}–{@code user_99888}：普通用户，其中前 {@value #REGULAR_AUTHORS} 个是写文章的作者，
 *     每人关注 20–75 个作者。</li>
 * </ul>
 *
 * <p>主键与应用一样用雪花 ID 的格式：高位是创建时间，低 22 位（应用里是机器号加序号）在这里填行序号。
 * 行数不超过 2^22 的表低位各不相同；关注表超过这个行数，按行序号单调分配创建时间，同一毫秒内至多一行。
 */
public class Seeder {

    static final long SEED = 20260926L;

    static final String PASSWORD = "loadtest123";

    static final int USERS = 100_000;
    static final int BIG_VS = 10;
    static final int BIG_V_FANS = 20_000;
    static final int BIG_V_ARTICLES = 200;
    static final int HEAVY_USERS = 100;
    static final int HEAVY_FOLLOWS = 500;
    static final int AUTHOR_4999_FANS = 4_999;
    static final int AUTHOR_4999_ARTICLES = 50;
    static final int REGULAR_AUTHORS = 20_000;
    static final int ARTICLES = 100_000;

    /**
     * 各角色在用户数组中的起始下标。
     */
    private static final int FIRST_HEAVY = BIG_VS;
    private static final int AUTHOR_4999 = FIRST_HEAVY + HEAVY_USERS;
    private static final int FIRST_REGULAR = AUTHOR_4999 + 1;

    private static final String JDBC_URL = "jdbc:mysql://localhost:3306/code_nest"
            + "?rewriteBatchedStatements=true&allowPublicKeyRetrieval=true&useSSL=false";

    /**
     * 与应用的 {@code DateTimes.ZONE} 一致，DATETIME 字段存的是这个时区的本地时间。
     */
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private static final long SNOWFLAKE_EPOCH = 1288834974657L;
    private static final int SEQUENCE_BITS = 22;

    /**
     * 每批 insert 的行数，驱动把一批改写成一条多行 insert；每批提交一次。
     */
    private static final int BATCH_SIZE = 1000;

    private static final double DRAFT_RATIO = 0.05;

    private static final List<String> TABLES = List.of("user", "article", "article_content", "article_tag",
            "follow", "article_like", "favorite", "comment");

    private final Connection connection;
    private final Random random = new Random(SEED);
    private final TextGenerator text = new TextGenerator(random);
    private final LocalDateTime now = LocalDateTime.now(ZONE).truncatedTo(ChronoUnit.SECONDS);

    private final long[] userIds = new long[USERS];
    private final long[] articleIds = new long[ARTICLES];
    private final int[] articleAuthors = new int[ARTICLES];
    /**
     * 草稿为 null。
     */
    private final LocalDateTime[] publishedAt = new LocalDateTime[ARTICLES];
    private int[] published;

    private Seeder(Connection connection) {
        this.connection = connection;
    }

    public static void main(String[] args) throws SQLException {
        try (Connection connection = DriverManager.getConnection(JDBC_URL, "codenest", "codenest")) {
            connection.setAutoCommit(false);
            new Seeder(connection).run();
        }
    }

    private void run() throws SQLException {
        requireEmpty();
        long start = System.nanoTime();
        timed("user", this::seedUsers);
        timed("article, article_content, article_tag", this::seedArticles);
        timed("follow", this::seedFollows);
        timed("article_like", this::seedLikes);
        timed("favorite", this::seedFavorites);
        timed("comment", this::seedComments);
        System.out.printf("造数完成，共 %s%n", Duration.ofNanos(System.nanoTime() - start).truncatedTo(ChronoUnit.SECONDS));
        for (String table : TABLES) {
            System.out.printf("%-16s %,d%n", table, count(table));
        }
    }

    private void requireEmpty() throws SQLException {
        if (count("user") > 0) {
            throw new IllegalStateException("user 表不为空：造数要从空库开始，先删除压测环境的数据卷再启动");
        }
    }

    private void seedUsers() throws SQLException {
        String hash = new BCryptPasswordEncoder().encode(PASSWORD);
        LocalDateTime from = now.minusDays(400);
        LocalDateTime to = now.minusDays(366);
        try (Batch users = new Batch("INSERT INTO `user` (id, username, password_hash, nickname, created_at, updated_at)"
                + " VALUES (?, ?, ?, ?, ?, ?)")) {
            for (int i = 0; i < USERS; i++) {
                LocalDateTime at = spread(from, to, i, USERS);
                userIds[i] = id(at, i);
                String username = username(i);
                users.add(userIds[i], username, hash, username, at, at);
            }
        }
    }

    private static String username(int index) {
        if (index < FIRST_HEAVY) {
            return "bigv_%02d".formatted(index);
        }
        if (index < AUTHOR_4999) {
            return "heavy_%03d".formatted(index - FIRST_HEAVY);
        }
        if (index == AUTHOR_4999) {
            return "author_4999";
        }
        return "user_%05d".formatted(index - FIRST_REGULAR);
    }

    /**
     * 文章按发布时间先后排列，作者打乱分配；约 5% 是草稿。发布时间分布在最近一年，最近 7 天的文章进入热榜候选。
     */
    private void seedArticles() throws SQLException {
        int next = 0;
        for (int bigV = 0; bigV < BIG_VS; bigV++) {
            for (int j = 0; j < BIG_V_ARTICLES; j++) {
                articleAuthors[next++] = bigV;
            }
        }
        for (int j = 0; j < AUTHOR_4999_ARTICLES; j++) {
            articleAuthors[next++] = AUTHOR_4999;
        }
        while (next < ARTICLES) {
            articleAuthors[next++] = FIRST_REGULAR + random.nextInt(REGULAR_AUTHORS);
        }
        shuffle(articleAuthors);

        LocalDateTime from = now.minusDays(365);
        LocalDateTime to = now.minusHours(1);
        List<Integer> publishedIndices = new ArrayList<>();
        try (Batch articles = new Batch("INSERT INTO article (id, author_id, category_id, title, summary, status,"
                + " published_at, version, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
             Batch contents = new Batch("INSERT INTO article_content (article_id, content, created_at, updated_at)"
                     + " VALUES (?, ?, ?, ?)");
             Batch tags = new Batch("INSERT INTO article_tag (article_id, tag_id, created_at, updated_at)"
                     + " VALUES (?, ?, ?, ?)")) {
            for (int i = 0; i < ARTICLES; i++) {
                LocalDateTime at = spread(from, to, i, ARTICLES);
                boolean draft = random.nextDouble() < DRAFT_RATIO;
                // 与应用一样，草稿创建时分配 ID，发布时版本号加 1
                LocalDateTime createdAt = draft ? at : at.minusHours(1);
                articleIds[i] = id(createdAt, i);
                if (!draft) {
                    publishedAt[i] = at;
                    publishedIndices.add(i);
                }
                String content = text.content();
                articles.add(articleIds[i], userIds[articleAuthors[i]], 1 + random.nextInt(9), text.title(),
                        TextGenerator.summaryOf(content), draft ? 0 : 1, publishedAt[i], draft ? 0 : 1, createdAt, at);
                contents.add(articleIds[i], content, createdAt, at);
                for (int tag : sample(30, 1 + random.nextInt(5), t -> true)) {
                    tags.add(articleIds[i], tag + 1, createdAt, createdAt);
                }
            }
        }
        published = publishedIndices.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * 先按关注者生成全部关注关系，再按行序号单调分配关注时间写入。大 V 与 author_4999 的粉丝单独抽取，
     * 普通用户随机关注的作者池不含他们，保证粉丝数恰好是设定值。
     */
    private void seedFollows() throws SQLException {
        BitSet[] bigVFans = new BitSet[BIG_VS];
        for (int bigV = 0; bigV < BIG_VS; bigV++) {
            bigVFans[bigV] = new BitSet(USERS);
            bigVFans[bigV].set(FIRST_HEAVY, AUTHOR_4999);
            for (int fan : sample(USERS - FIRST_REGULAR, BIG_V_FANS - HEAVY_USERS, u -> true)) {
                bigVFans[bigV].set(FIRST_REGULAR + fan);
            }
        }
        BitSet author4999Fans = new BitSet(USERS);
        for (int fan : sample(USERS - FIRST_REGULAR, AUTHOR_4999_FANS, u -> true)) {
            author4999Fans.set(FIRST_REGULAR + fan);
        }

        int[] followers = new int[5_200_000];
        int[] authors = new int[followers.length];
        int rows = 0;
        for (int follower = 0; follower < USERS; follower++) {
            List<Integer> followed = new ArrayList<>();
            boolean heavy = follower >= FIRST_HEAVY && follower < AUTHOR_4999;
            for (int bigV = 0; bigV < BIG_VS; bigV++) {
                if (bigVFans[bigV].get(follower)) {
                    followed.add(bigV);
                }
            }
            if (author4999Fans.get(follower)) {
                followed.add(AUTHOR_4999);
            }
            int regular = heavy ? HEAVY_FOLLOWS - BIG_VS : 20 + random.nextInt(56);
            int self = follower - FIRST_REGULAR;
            for (int author : sample(REGULAR_AUTHORS, regular, a -> a != self)) {
                followed.add(FIRST_REGULAR + author);
            }
            if (rows + followed.size() > followers.length) {
                followers = Arrays.copyOf(followers, followers.length * 2);
                authors = Arrays.copyOf(authors, followers.length);
            }
            for (int author : followed) {
                followers[rows] = follower;
                authors[rows++] = author;
            }
        }

        LocalDateTime from = now.minusDays(365);
        try (Batch follows = new Batch("INSERT INTO follow (id, follower_id, author_id, created_at, updated_at)"
                + " VALUES (?, ?, ?, ?, ?)")) {
            for (int row = 0; row < rows; row++) {
                LocalDateTime at = spread(from, now, row, rows);
                follows.add(id(at, row), userIds[followers[row]], userIds[authors[row]], at, at);
            }
        }
    }

    /**
     * 每个用户点赞 0–40 篇别人的已发布文章，点赞行记下文章作者。
     */
    private void seedLikes() throws SQLException {
        try (Batch likes = new Batch("INSERT INTO article_like (id, user_id, article_id, author_id, created_at,"
                + " updated_at) VALUES (?, ?, ?, ?, ?, ?)")) {
            int row = 0;
            for (int user = 0; user < USERS; user++) {
                for (int article : samplePublishedByOthers(user, random.nextInt(41))) {
                    LocalDateTime at = between(publishedAt[article], now);
                    likes.add(id(at, row++), userIds[user], articleIds[article], userIds[articleAuthors[article]], at,
                            at);
                }
            }
        }
    }

    /**
     * 每个用户收藏 0–10 篇别人的已发布文章。
     */
    private void seedFavorites() throws SQLException {
        try (Batch favorites = new Batch("INSERT INTO favorite (id, user_id, article_id, created_at, updated_at)"
                + " VALUES (?, ?, ?, ?, ?)")) {
            int row = 0;
            for (int user = 0; user < USERS; user++) {
                for (int article : samplePublishedByOthers(user, random.nextInt(11))) {
                    LocalDateTime at = between(publishedAt[article], now);
                    favorites.add(id(at, row++), userIds[user], articleIds[article], at, at);
                }
            }
        }
    }

    /**
     * 每篇已发布文章 0–6 条评论，每条评论 0–2 条回复；第一条回复回复评论本身，之后的回复 @ 上一个回复者。
     */
    private void seedComments() throws SQLException {
        try (Batch comments = new Batch("INSERT INTO comment (id, article_id, user_id, root_id, reply_to_user_id,"
                + " content, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            int row = 0;
            for (int article : published) {
                int commentCount = random.nextInt(7);
                for (int c = 0; c < commentCount; c++) {
                    LocalDateTime at = between(publishedAt[article], now);
                    long commentId = id(at, row++);
                    long commenter = userIds[random.nextInt(USERS)];
                    comments.add(commentId, articleIds[article], commenter, 0L, null, text.comment(), at, at);
                    Long replyTo = null;
                    int replyCount = random.nextInt(3);
                    for (int r = 0; r < replyCount; r++) {
                        at = between(at, now);
                        long replier = userIds[random.nextInt(USERS)];
                        comments.add(id(at, row++), articleIds[article], replier, commentId, replyTo, text.comment(),
                                at, at);
                        replyTo = replier;
                    }
                }
            }
        }
    }

    private int[] samplePublishedByOthers(int user, int count) {
        int[] picked = sample(published.length, count, p -> articleAuthors[published[p]] != user);
        for (int i = 0; i < picked.length; i++) {
            picked[i] = published[picked[i]];
        }
        return picked;
    }

    /**
     * 从 [0, bound) 中不放回地抽取 count 个满足 allowed 的数，按抽中的先后排列。
     */
    private int[] sample(int bound, int count, IntPredicate allowed) {
        Set<Integer> picked = new LinkedHashSet<>();
        while (picked.size() < count) {
            int candidate = random.nextInt(bound);
            if (allowed.test(candidate)) {
                picked.add(candidate);
            }
        }
        return picked.stream().mapToInt(Integer::intValue).toArray();
    }

    private void shuffle(int[] values) {
        for (int i = values.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int value = values[i];
            values[i] = values[j];
            values[j] = value;
        }
    }

    /**
     * 第 index 个（共 total 个）在 [from, to] 上等距分布的时间，随 index 单调不减。
     */
    private static LocalDateTime spread(LocalDateTime from, LocalDateTime to, long index, long total) {
        long span = Duration.between(from, to).toMillis();
        return from.plus(span * index / total, ChronoUnit.MILLIS);
    }

    /**
     * [from, to] 上的随机时间，精确到秒。
     */
    private LocalDateTime between(LocalDateTime from, LocalDateTime to) {
        long seconds = Duration.between(from, to).toSeconds();
        return from.plusSeconds(seconds <= 0 ? 0 : random.nextLong(seconds + 1));
    }

    /**
     * 雪花格式的 ID：高位是创建时间的毫秒数，低 22 位是行序号截断后的值。唯一性的前提见类注释。
     */
    private static long id(LocalDateTime at, long sequence) {
        long millis = at.atZone(ZONE).toInstant().toEpochMilli();
        return (millis - SNOWFLAKE_EPOCH) << SEQUENCE_BITS | (sequence & ((1L << SEQUENCE_BITS) - 1));
    }

    private long count(String table) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM `" + table + "`");
             ResultSet result = statement.executeQuery()) {
            result.next();
            return result.getLong(1);
        }
    }

    private void timed(String name, Step step) throws SQLException {
        long start = System.nanoTime();
        step.run();
        System.out.printf("%-40s %s%n", name, Duration.ofNanos(System.nanoTime() - start).truncatedTo(ChronoUnit.SECONDS));
    }

    @FunctionalInterface
    private interface Step {
        void run() throws SQLException;
    }

    /**
     * 攒满 {@value #BATCH_SIZE} 行执行一次批量 insert 并提交，关闭时写入剩余的行。
     */
    private final class Batch implements AutoCloseable {

        private final PreparedStatement statement;
        private int pending;

        private Batch(String sql) throws SQLException {
            this.statement = connection.prepareStatement(sql);
        }

        void add(Object... values) throws SQLException {
            for (int i = 0; i < values.length; i++) {
                statement.setObject(i + 1, values[i]);
            }
            statement.addBatch();
            if (++pending == BATCH_SIZE) {
                flush();
            }
        }

        private void flush() throws SQLException {
            if (pending > 0) {
                statement.executeBatch();
                connection.commit();
                pending = 0;
            }
        }

        @Override
        public void close() throws SQLException {
            try {
                flush();
            } finally {
                statement.close();
            }
        }
    }
}
