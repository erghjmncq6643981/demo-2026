package com.chandler.fcc.admin.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.fcc.admin.infrastructure.persistence.entity.DidNumberEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 呼入引示号 DID 数据访问 Mapper 接口
 *
 * @author Chandler
 */
@Mapper
public interface DidNumberMapper extends BaseMapper<DidNumberEntity> {
}
