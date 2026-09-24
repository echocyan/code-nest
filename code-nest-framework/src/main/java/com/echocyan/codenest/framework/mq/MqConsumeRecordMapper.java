package com.echocyan.codenest.framework.mq;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MqConsumeRecordMapper extends BaseMapper<MqConsumeRecord> {
}
