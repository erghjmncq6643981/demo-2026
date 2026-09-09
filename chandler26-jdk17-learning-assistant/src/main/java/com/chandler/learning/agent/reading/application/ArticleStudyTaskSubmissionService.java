package com.chandler.learning.agent.reading.application;

import com.chandler.learning.agent.reading.api.request.ArticleStudyRequest;
import com.chandler.learning.agent.task.application.AiAsyncTaskService;
import com.chandler.learning.agent.task.domain.constant.AiTaskConstants;
import com.chandler.learning.agent.task.domain.entity.AiAsyncTask;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 语境精读 AI 任务提交边界。
 * <p>生成文章属于耗时动作，HTTP 层只创建任务并返回摘要，Worker 在提交后调用 {@link ArticleStudyService} 保存结果。</p>
 */
@Service
@RequiredArgsConstructor
public class ArticleStudyTaskSubmissionService {

    private final AiAsyncTaskService taskService;
    private final ObjectMapper objectMapper;

    /** 创建幂等的文章生成任务。 */
    public AiAsyncTask submit(Long userId, ArticleStudyRequest request) {
        Map<String, Object> payload = objectMapper.convertValue(request, new TypeReference<>() {
        });
        List<Long> entryIds = new ArrayList<>(request.getEntryIds() == null ? List.of() : request.getEntryIds());
        entryIds.removeIf(java.util.Objects::isNull);
        entryIds = entryIds.stream().distinct().sorted().toList();
        String idempotencyKey = "article_material:" + userId + ":" + request.getWordbookId()
                + ":" + entryIds + ":" + request.getWordCountRange() + ":" + request.getDifficulty();
        AiAsyncTask active = taskService.findActiveByKey(userId,
                AiTaskConstants.TYPE_ARTICLE_MATERIAL, null, idempotencyKey);
        if (active != null) {
            return active;
        }
        return taskService.create(userId, AiTaskConstants.TYPE_ARTICLE_MATERIAL,
                "生成语境精读材料", null, null, null,
                AiTaskConstants.EXECUTION_IMMEDIATE, null, null, 1,
                idempotencyKey, new HashMap<>(payload));
    }
}
