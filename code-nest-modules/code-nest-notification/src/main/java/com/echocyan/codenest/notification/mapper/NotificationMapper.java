package com.echocyan.codenest.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.echocyan.codenest.notification.entity.Notification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {

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
