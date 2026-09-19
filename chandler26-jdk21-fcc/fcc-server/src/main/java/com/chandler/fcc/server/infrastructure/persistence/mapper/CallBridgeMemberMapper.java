package com.chandler.fcc.server.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.fcc.server.infrastructure.persistence.entity.CallBridgeMemberEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 桥接成员话道关联持久层 Mapper
 * <p>
 * 提供对 fcc_call_bridge_member 表的 CRUD 操作接口。
 * </p>
 *
 * @author Chandler
 */
@Mapper
public interface CallBridgeMemberMapper extends BaseMapper<CallBridgeMemberEntity> {
}
