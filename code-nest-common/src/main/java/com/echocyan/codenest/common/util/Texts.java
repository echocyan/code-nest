package com.echocyan.codenest.common.util;

/**
 * 文本工具。
 */
public final class Texts {

    private Texts() {
    }

    /**
     * 截取开头至多 maxCodePoints 个字符。按码点截取，避免切开 emoji 等代理对。
     */
    public static String head(String text, int maxCodePoints) {
        int length = Math.min(maxCodePoints, text.codePointCount(0, text.length()));
        return text.substring(0, text.offsetByCodePoints(0, length));
    }
}
