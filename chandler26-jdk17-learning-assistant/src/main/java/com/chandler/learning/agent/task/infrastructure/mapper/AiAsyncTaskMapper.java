package com.chandler.learning.agent.task.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chandler.learning.agent.task.domain.entity.AiAsyncTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * AI 异步任务数据访问。
 */
@Mapper
public interface AiAsyncTaskMapper extends BaseMapper<AiAsyncTask> {

    /**
     * 分页读取任务摘要，明确排除 payload_json，避免任务列表传输和反序列化大载荷。
     */
    Page<AiAsyncTask> selectSummaryPage(Page<AiAsyncTask> page,
                                        @Param("ownerUserId") Long ownerUserId,
                                        @Param("status") String status);

    /** 查询活动场景任务日期投影，不加载任务载荷 JSON。 */
    List<LocalDate> selectActiveGeneratingDates(@Param("ownerUserId") Long ownerUserId,
                                                @Param("planId") Long planId,
                                                @Param("sceneMaterialType") String sceneMaterialType,
                                                @Param("sceneRegenerationType") String sceneRegenerationType,
                                                @Param("pendingStatus") String pendingStatus,
                                                @Param("runningStatus") String runningStatus,
                                                @Param("retryWaitStatus") String retryWaitStatus);

    /** 根据幂等键查询最近一条未删除的 AI 异步任务。 */
    AiAsyncTask selectLatestByIdempotencyKey(@Param("ownerUserId") Long ownerUserId,
                                             @Param("taskType") String taskType,
                                             @Param("idempotencyKey") String idempotencyKey);

    /** 查询指定单词本与场景下最新一条未删除的异步任务，优先返回仍处于活动状态的任务。 */
    AiAsyncTask selectLatestByJobAndScenario(@Param("ownerUserId") Long ownerUserId,
                                             @Param("taskType") String taskType,
                                             @Param("relatedJobId") Long relatedJobId,
                                             @Param("scenarioCode") String scenarioCode,
                                             @Param("pendingStatus") String pendingStatus,
                                             @Param("runningStatus") String runningStatus,
                                             @Param("retryWaitStatus") String retryWaitStatus);

    /** 查询指定业务关联对象（如单词本）下多类型异步任务，优先返回仍处于活动状态的任务。 */
    List<AiAsyncTask> selectRecentByRelatedJobAndTypes(@Param("ownerUserId") Long ownerUserId,
                                                       @Param("relatedJobId") Long relatedJobId,
                                                       @Param("taskTypes") List<String> taskTypes,
                                                       @Param("pendingStatus") String pendingStatus,
                                                       @Param("runningStatus") String runningStatus,
                                                       @Param("retryWaitStatus") String retryWaitStatus,
                                                       @Param("limit") Integer limit);
}
