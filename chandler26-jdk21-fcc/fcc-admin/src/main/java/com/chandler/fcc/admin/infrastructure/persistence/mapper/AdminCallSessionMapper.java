package com.chandler.fcc.admin.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.fcc.admin.infrastructure.persistence.entity.CallSessionEntity;
import java.time.LocalDateTime;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 管理台通话会话历史 CDR 数据访问 Mapper 接口
 *
 * @author Chandler
 */
@Mapper
public interface AdminCallSessionMapper extends BaseMapper<CallSessionEntity> {
  /**
   * 按统计区间聚合话单指标
   * <p>
   * 与分页列表解耦：KPI 卡片直接由数据库做全量聚合，避免"只统计到当前页"的失真。
   * </p>
   *
   * @param startTime 统计区间起点 (含)
   * @return 聚合结果行，键为 totalCalls / answeredCalls / totalTalkMs / inboundTalkMs / outboundTalkMs
   */
  Map<String, Object> selectStatsSince(@Param("startTime") LocalDateTime startTime);
}
