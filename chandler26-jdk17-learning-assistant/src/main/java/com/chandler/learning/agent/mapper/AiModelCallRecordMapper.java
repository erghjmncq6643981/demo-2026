package com.chandler.learning.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.domain.entity.AiModelCallRecord;
import com.chandler.learning.agent.domain.dto.AiModelUsageSummary;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * AiModelCallRecordMapper 类。
 */
@Mapper
public interface AiModelCallRecordMapper extends BaseMapper<AiModelCallRecord> {

    /** 按供应商和模型聚合调用指标。 */
    List<AiModelUsageSummary> selectUsageSummaries();
}
