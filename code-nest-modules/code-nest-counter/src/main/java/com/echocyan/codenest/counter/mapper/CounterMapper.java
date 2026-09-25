package com.echocyan.codenest.counter.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

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

    /**
     * 批量写入绝对值，没有计数行时插入。每行依次是对象 ID 和 columns 中各列的值。
     */
    @Insert("""
            <script>
            INSERT INTO ${table} (${idColumn},
            <foreach collection="columns" item="column" separator=",">${column}</foreach>, created_at, updated_at)
            VALUES
            <foreach collection="rows" item="row" separator=",">
            (<foreach collection="row" item="value" separator=",">#{value}</foreach>, #{now}, #{now})
            </foreach>
            AS new ON DUPLICATE KEY UPDATE
            <foreach collection="columns" item="column" separator=",">${column} = new.${column}</foreach>,
            updated_at = new.updated_at
            </script>
            """)
    void upsertAll(@Param("table") String table, @Param("idColumn") String idColumn,
                   @Param("columns") List<String> columns, @Param("rows") List<List<Long>> rows,
                   @Param("now") LocalDateTime now);

    @Select("""
            <script>
            SELECT * FROM ${table} WHERE ${idColumn} IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<Map<String, Object>> selectByIds(@Param("table") String table, @Param("idColumn") String idColumn,
                                          @Param("ids") Collection<Long> ids);

    /**
     * ID 在 (afterId, upperId] 内、该列不为 0 的对象，走主键范围扫描。
     */
    @Select("""
            SELECT ${idColumn} FROM ${table}
            WHERE ${idColumn} > #{afterId} AND ${idColumn} <= #{upperId} AND ${column} > 0
            """)
    List<Long> selectNonZeroIds(@Param("table") String table, @Param("idColumn") String idColumn,
                                @Param("column") String column, @Param("afterId") long afterId,
                                @Param("upperId") long upperId);
}
