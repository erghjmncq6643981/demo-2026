package com.chandler.fcc.admin.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.fcc.admin.infrastructure.persistence.entity.ClientHardwareRecordEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 坐席客户端硬件安全指纹审计数据访问 Mapper 接口
 *
 * @author Chandler
 */
@Mapper
public interface ClientHardwareRecordMapper extends BaseMapper<ClientHardwareRecordEntity> {
}
