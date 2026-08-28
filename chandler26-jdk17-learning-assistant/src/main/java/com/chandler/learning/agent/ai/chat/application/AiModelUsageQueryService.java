package com.chandler.learning.agent.ai.chat.application;

import com.chandler.learning.agent.ai.chat.infrastructure.mapper.AiModelCallRecordMapper;
import com.chandler.learning.agent.ai.model.domain.bo.AiModelUsageSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** AI 调用记录的跨域只读查询入口。 */
@Service
@RequiredArgsConstructor
public class AiModelUsageQueryService {

    private final AiModelCallRecordMapper modelCallRecordMapper;

    /** 按供应商和模型聚合调用次数、Token 与耗时。 */
    public List<AiModelUsageSummary> listUsageSummaries() {
        return modelCallRecordMapper.selectUsageSummaries();
    }
}
