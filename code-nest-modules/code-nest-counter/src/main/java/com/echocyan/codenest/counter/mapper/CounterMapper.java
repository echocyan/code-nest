package com.echocyan.codenest.counter.mapper;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 三张计数表共用的 SQL。表名、列名由调用方从枚举推导，不来自用户输入。
 */
@Mapper
public interface CounterMapper {

    /**
     * 计数行不存在时插入，存在时累加；结果不低于 0。
     */
    @Insert("""
            INSERT INTO ${table} (${idColumn}, ${column}, created_at, updated_at)
            VALUES (#{id}, GREATEST(#{delta}, 0), #{now}, #{now})
            ON DUPLICATE KEY UPDATE ${column} = GREATEST(${column} + #{delta}, 0), updated_at = #{now}
            """)
    void increment(@Param("table") String table, @Param("idColumn") String idColumn, @Param("column") String column,
                   @Param("id") long id, @Param("delta") long delta, @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO ${table} (${idColumn}, ${column}, created_at, updated_at)
            VALUES (#{id}, #{value}, #{now}, #{now})
            ON DUPLICATE KEY UPDATE ${column} = #{value}, updated_at = #{now}
            """)
    void reset(@Param("table") String table, @Param("idColumn") String idColumn, @Param("column") String column,
               @Param("id") long id, @Param("value") long value, @Param("now") LocalDateTime now);

    @Select("""
            <script>
            SELECT * FROM ${table} WHERE ${idColumn} IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<Map<String, Object>> selectByIds(@Param("table") String table, @Param("idColumn") String idColumn,
                                          @Param("ids") Collection<Long> ids);
}
