package com.chandler.fcc.server.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.fcc.server.infrastructure.persistence.entity.CallRecordingEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 通话录音元数据持久层 Mapper
 * <p>
 * 提供对 fcc_call_recording 表的 CRUD 操作接口。
 * </p>
 *
 * @author Chandler
 */
@Mapper
public interface CallRecordingMapper extends BaseMapper<CallRecordingEntity> {
}
