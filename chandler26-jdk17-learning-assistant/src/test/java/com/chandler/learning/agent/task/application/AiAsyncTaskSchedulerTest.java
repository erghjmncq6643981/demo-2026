package com.chandler.learning.agent.task.application;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.chandler.learning.agent.task.domain.constant.AiTaskConstants;
import com.chandler.learning.agent.task.domain.entity.AiAsyncTask;
import com.chandler.learning.agent.task.infrastructure.mapper.AiAsyncTaskMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiAsyncTaskSchedulerTest {

    @Mock
    private AiAsyncTaskMapper taskMapper;

    @Mock
    private AiAsyncTaskService taskService;

    @Mock
    private AiAsyncTaskDispatcher dispatcher;

    @Mock
    private AiTaskExecutionService executionService;

    private AiAsyncTaskScheduler scheduler;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AiAsyncTask.class);
        scheduler = new AiAsyncTaskScheduler(taskMapper, taskService, dispatcher, executionService);
    }

    @Test
    @DisplayName("同一计划下的多个待执行任务串行派发，仅派发第 1 个任务，后续任务排队等待")
    void dispatchesTasksOfSamePlanSerially() {
        when(taskMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of()) // first query for occupiedPlanIds
                .thenReturn(List.of(   // second query for pending tasks
                        createTask(101L, 1001L, "2026-08-31"),
                        createTask(102L, 1001L, "2026-09-01"),
                        createTask(103L, 1001L, "2026-09-02")
                ));

        when(taskService.claim(101L)).thenReturn(true);

        scheduler.dispatchDueTasks();

        verify(taskService).claim(101L);
        verify(dispatcher).dispatch(argThatTask(101L));

        verify(taskService, never()).claim(102L);
        verify(taskService, never()).claim(103L);
        verify(dispatcher, never()).dispatch(argThatTask(102L));
        verify(dispatcher, never()).dispatch(argThatTask(103L));
    }

    @Test
    @DisplayName("若计划已有任务处于执行中且材料步骤未完成，后续新任务保持排队不派发")
    void skipsDispatchWhenPlanHasRunningTask() {
        AiAsyncTask runningTask = createTask(99L, 1001L, "2026-08-30");
        runningTask.setStatus(AiTaskConstants.STATUS_RUNNING);

        when(executionService.isMaterialStepCompleted(99L, AiTaskConstants.TYPE_SCENE_MATERIAL))
                .thenReturn(false);

        when(taskMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(runningTask)) // occupied query
                .thenReturn(List.of(              // pending query
                        createTask(101L, 1001L, "2026-08-31")
                ));

        scheduler.dispatchDueTasks();

        verify(taskService, never()).claim(101L);
        verify(dispatcher, never()).dispatch(any());
    }

    @Test
    @DisplayName("流水线重叠：若正在执行的任务已完成材料生成步骤，后续任务可立即派发执行")
    void dispatchesNextTaskWhenMaterialStepIsCompleted() {
        AiAsyncTask runningTask = createTask(99L, 1001L, "2026-08-30");
        runningTask.setStatus(AiTaskConstants.STATUS_RUNNING);

        // runningTask 的材料生成步骤已 COMPLETED
        when(executionService.isMaterialStepCompleted(99L, AiTaskConstants.TYPE_SCENE_MATERIAL))
                .thenReturn(true);

        when(taskMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(runningTask)) // occupied query
                .thenReturn(List.of(              // pending query
                        createTask(101L, 1001L, "2026-08-31")
                ));

        when(taskService.claim(101L)).thenReturn(true);

        scheduler.dispatchDueTasks();

        // 101L 成功被派发（与 99L 重叠并行）
        verify(taskService).claim(101L);
        verify(dispatcher).dispatch(argThatTask(101L));
    }

    @Test
    @DisplayName("不同计划的任务或无 planId 的独立任务可并发派发")
    void dispatchesTasksOfDifferentPlansConcurrently() {
        AiAsyncTask taskA = createTask(101L, 1001L, "2026-08-31");
        AiAsyncTask taskB = createTask(102L, 2002L, "2026-08-31");
        AiAsyncTask taskIndependent = createTask(103L, null, null);

        when(taskMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of()) // occupied query
                .thenReturn(List.of(taskA, taskB, taskIndependent)); // pending query

        when(taskService.claim(101L)).thenReturn(true);
        when(taskService.claim(102L)).thenReturn(true);
        when(taskService.claim(103L)).thenReturn(true);

        scheduler.dispatchDueTasks();

        verify(taskService).claim(101L);
        verify(taskService).claim(102L);
        verify(taskService).claim(103L);
        verify(dispatcher).dispatch(argThatTask(101L));
        verify(dispatcher).dispatch(argThatTask(102L));
        verify(dispatcher).dispatch(argThatTask(103L));
    }

    @Test
    @DisplayName("应用启动时自动重置 running 任务与步骤并立即触发调度")
    void recoversRunningTasksAndStepsOnApplicationReady() {
        when(executionService.recoverAllRunning(any())).thenReturn(2);
        when(taskMapper.update(any(), any())).thenReturn(1);
        when(taskMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of())
                .thenReturn(List.of());

        scheduler.onApplicationReady();

        verify(executionService).recoverAllRunning(any());
        verify(taskMapper, org.mockito.Mockito.atLeastOnce()).update(any(), any());
    }

    private AiAsyncTask createTask(Long id, Long planId, String date) {
        AiAsyncTask task = new AiAsyncTask();
        task.setId(id);
        task.setPlanId(planId);
        task.setTaskType(AiTaskConstants.TYPE_SCENE_MATERIAL);
        task.setStatus(AiTaskConstants.STATUS_PENDING);
        task.setPriority(1);
        task.setScheduledTime(LocalDateTime.now());
        task.setCreateTime(LocalDateTime.now());
        task.setDeleted(false);
        return task;
    }

    private AiAsyncTask argThatTask(Long id) {
        return org.mockito.ArgumentMatchers.argThat(task -> task != null && id.equals(task.getId()));
    }
}
