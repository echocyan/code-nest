package com.echocyan.codenest.loadtest;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;

/**
 * 用技术词库加句子模板生成中文的标题、正文与评论。词库在 {@code vocabulary.txt}，搜索压测也从中取关键词。
 * 同一个 {@link Random} 按同样的顺序调用，生成的文本相同。
 */
class TextGenerator {

    private static final List<String> TITLES = List.of(
            "深入理解%s",
            "%s实战：从%s到%s",
            "%s与%s的取舍",
            "一次%s线上故障的复盘",
            "%s在高并发场景下的优化",
            "面试必问：%s",
            "图解%s",
            "%s最佳实践",
            "用%s解决%s问题",
            "%s源码剖析",
            "从零搭建%s",
            "%s踩坑记录");

    private static final List<String> SENTENCES = List.of(
            "%s的核心在于%s，理解了这一点，%s就不难了。",
            "很多人把%s和%s混为一谈，其实两者解决的是不同的问题。",
            "在生产环境里使用%s时，一定要先评估%s带来的开销。",
            "我们最初用%s实现，后来因为%s的瓶颈改成了%s。",
            "压测数据显示，引入%s之后接口的 P99 明显下降。",
            "如果只看%s，很容易忽略%s对整体性能的影响。",
            "%s并不是银弹，它和%s一样都有适用的边界。",
            "排查问题时先看%s，再结合%s的日志定位根因。",
            "官方文档对%s的描述比较简略，这里结合%s补充一些细节。",
            "团队最终决定保留%s，同时用%s兜底。",
            "这一步看似简单，却是%s能否稳定运行的关键。",
            "对比%s和%s的实现，可以更好地理解%s的设计取舍。");

    private static final List<String> COMMENTS = List.of(
            "写得很清楚，%s这部分终于看懂了。",
            "请教一下，%s和%s同时用会有问题吗？",
            "我们项目也遇到过%s的坑，最后换成了%s。",
            "收藏了，回头对照%s的源码再看一遍。",
            "感谢分享，期待后续讲讲%s。",
            "有个疑问：文中%s的结论在高并发下还成立吗？");

    private final List<String> words;
    private final Random random;

    TextGenerator(Random random) {
        this.random = random;
        try (InputStream in = TextGenerator.class.getResourceAsStream("/vocabulary.txt")) {
            this.words = new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(String::strip)
                    .filter(line -> !line.isEmpty())
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    String title() {
        return fill(pick(TITLES));
    }

    /**
     * Markdown 正文：3–5 节，每节一个小标题和 1–2 段，每段 3–5 句。
     */
    String content() {
        StringBuilder content = new StringBuilder();
        int sections = 3 + random.nextInt(3);
        for (int s = 0; s < sections; s++) {
            content.append("## ").append(word()).append("\n\n");
            int paragraphs = 1 + random.nextInt(2);
            for (int p = 0; p < paragraphs; p++) {
                int sentences = 3 + random.nextInt(3);
                for (int i = 0; i < sentences; i++) {
                    content.append(fill(pick(SENTENCES)));
                }
                content.append("\n\n");
            }
        }
        return content.toString();
    }

    /**
     * 与作者未填写摘要时一样，取正文开头 100 个字符。
     */
    static String summaryOf(String content) {
        String text = content.strip();
        return text.length() <= 100 ? text : text.substring(0, 100);
    }

    String comment() {
        return fill(pick(COMMENTS));
    }

    private String fill(String template) {
        int slots = template.split("%s", -1).length - 1;
        Object[] args = new Object[slots];
        for (int i = 0; i < slots; i++) {
            args[i] = word();
        }
        return template.formatted(args);
    }

    private String word() {
        return pick(words);
    }

    private String pick(List<String> list) {
        return list.get(random.nextInt(list.size()));
    }
}
