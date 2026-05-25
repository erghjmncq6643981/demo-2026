package com.chandler.motivation.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.motivation.domain.dataobject.MotivationTaskRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MotivationTaskRecordMapper extends BaseMapper<MotivationTaskRecord> {
}
