package com.chandler.motivation.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.motivation.domain.dataobject.MotivationTask;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MotivationTaskMapper extends BaseMapper<MotivationTask> {
}
