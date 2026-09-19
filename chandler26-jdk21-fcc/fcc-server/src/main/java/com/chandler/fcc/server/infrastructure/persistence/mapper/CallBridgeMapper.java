package com.chandler.fcc.server.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.fcc.server.infrastructure.persistence.entity.CallBridgeEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 通话媒体桥接持久层 Mapper
 * <p>
 * 提供对 fcc_call_bridge 表的 CRUD 操作接口。
 * </p>
 *
 * @author Chandler
 */
@Mapper
public interface CallBridgeMapper extends BaseMapper<CallBridgeEntity> {
}
