package com.chandler.fcc.server.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.fcc.server.infrastructure.persistence.entity.CallSessionEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 通话会话数据持久层 Mapper
 * <p>
 * 提供对 fcc_call_session 表的 CRUD 操作接口。
 * </p>
 *
 * @author Chandler
 */
@Mapper
public interface CallSessionMapper extends BaseMapper<CallSessionEntity> {
}
