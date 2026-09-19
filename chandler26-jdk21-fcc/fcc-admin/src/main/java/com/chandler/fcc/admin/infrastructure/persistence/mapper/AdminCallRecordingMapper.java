package com.chandler.fcc.admin.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.fcc.admin.infrastructure.persistence.entity.CallRecordingEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 管理台通话录音数据访问 Mapper 接口
 *
 * @author Chandler
 */
@Mapper
public interface AdminCallRecordingMapper extends BaseMapper<CallRecordingEntity> {
}
