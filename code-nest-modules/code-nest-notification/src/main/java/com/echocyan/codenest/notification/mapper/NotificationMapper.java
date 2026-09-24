package com.echocyan.codenest.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.echocyan.codenest.notification.entity.Notification;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {

    /**
     * dedup_key 冲突时忽略，不报错。自定义 SQL 不走自动填充，ID 与时间由调用方设置。
     *
     * @return 插入的行数，被忽略时为 0
     */
    @Insert("""
            INSERT IGNORE INTO notification
                (id, recipient_id, actor_id, type, article_id, comment_id, dedup_key, is_read, created_at, updated_at)
            VALUES (#{id}, #{recipientId}, #{actorId}, #{type}, #{articleId}, #{commentId}, #{dedupKey}, #{isRead},
                    #{createdAt}, #{updatedAt})
            """)
    int insertIgnore(Notification notification);

    /**
     * 未读数，最多数到 limit：子查询只扫描到 limit 行就停，走 (recipient_id, is_read) 索引。
     */
    @Select("""
            SELECT COUNT(*) FROM (
                SELECT 1 FROM notification WHERE recipient_id = #{recipientId} AND is_read = 0 LIMIT #{limit}
            ) t
            """)
    int countUnread(long recipientId, int limit);
}
