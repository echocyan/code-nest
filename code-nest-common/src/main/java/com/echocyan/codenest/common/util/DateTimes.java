package com.echocyan.codenest.common.util;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 全站统一时区。数据库 DATETIME 与实体 LocalDateTime 都按此时区解释。
 */
public final class DateTimes {

    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private DateTimes() {
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }
}
