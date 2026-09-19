package com.chandler.fcc.server.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.fcc.server.infrastructure.persistence.entity.CallCommandEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 话务指令审计数据持久层 Mapper
 * <p>
 * 提供对 fcc_call_command 表的 CRUD 操作接口。
 * </p>
 *
 * @author Chandler
 */
@Mapper
public interface CallCommandMapper extends BaseMapper<CallCommandEntity> {
}
