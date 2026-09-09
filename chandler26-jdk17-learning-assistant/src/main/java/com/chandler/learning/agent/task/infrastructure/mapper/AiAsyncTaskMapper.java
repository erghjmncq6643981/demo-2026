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
}
