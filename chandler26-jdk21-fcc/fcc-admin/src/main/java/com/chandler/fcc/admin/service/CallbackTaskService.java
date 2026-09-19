package com.chandler.fcc.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chandler.fcc.admin.infrastructure.persistence.entity.CallbackTaskEntity;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.CallbackTaskMapper;
import com.chandler.fcc.admin.model.PageResult;
import com.chandler.fcc.admin.model.dto.CallbackTaskAssignReq;
import com.chandler.fcc.admin.model.dto.CallbackTaskQueryReq;
import com.chandler.fcc.admin.model.vo.CallbackTaskVO;
import com.chandler.fcc.common.util.IdUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 未接待漏话待办与回拨总池业务服务
 * <p>
 * 提供漏话任务列表分页检索、坐席派单与一键回访状态更新。
 * </p>
 *
 * @author Chandler
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CallbackTaskService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CallbackTaskMapper callbackTaskMapper;



    /**
     * 分页查询漏话回拨任务列表
     *
     * @param req 查询入参
     * @return 分页结果
     */
    public PageResult<CallbackTaskVO> queryCallbacks(CallbackTaskQueryReq req) {
        Page<CallbackTaskEntity> page = new Page<>(req.getPageNum(), req.getPageSize());
        LambdaQueryWrapper<CallbackTaskEntity> wrapper = new LambdaQueryWrapper<CallbackTaskEntity>()
                .eq(req.getStatus() != null && !req.getStatus().isBlank(), CallbackTaskEntity::getStatus, req.getStatus())
                .like(req.getCustomerNumber() != null && !req.getCustomerNumber().isBlank(),
                        CallbackTaskEntity::getCustomerNumber, req.getCustomerNumber())
                .orderByDesc(CallbackTaskEntity::getPriority)
                .orderByDesc(CallbackTaskEntity::getMissedAt);

        Page<CallbackTaskEntity> entityPage = callbackTaskMapper.selectPage(page, wrapper);

        List<CallbackTaskVO> voList = entityPage.getRecords().stream()
                .map(this::buildCallbackVO)
                .toList();

        return PageResult.<CallbackTaskVO>builder()
                .pageNum(entityPage.getCurrent())
                .pageSize(entityPage.getSize())
                .total(entityPage.getTotal())
                .list(voList)
                .build();
    }

    /**
     * 指派跟进坐席
     *
     * @param id  任务 ID
     * @param req 派单入参
     */
    @Transactional(rollbackFor = Exception.class)
    public void assignTask(Long id, CallbackTaskAssignReq req) {
        CallbackTaskEntity entity = callbackTaskMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("漏话回拨任务不存在: " + id);
        }
        entity.setStatus("ASSIGNED");
        entity.setAssigneeWorkNo(req.getAgentWorkNo());
        entity.setAssigneeName(req.getAgentName());
        entity.setUpdatedAt(LocalDateTime.now());
        callbackTaskMapper.updateById(entity);
        log.info("[CallbackTaskService] 漏话任务派单成功: id={}, assignee={}", id, req.getAgentName());
    }

    /**
     * 发起一键优先回拨
     *
     * @param id 任务 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void callTask(Long id) {
        CallbackTaskEntity entity = callbackTaskMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("漏话回拨任务不存在: " + id);
        }
        entity.setStatus("CALLED");
        entity.setCallAttempts((entity.getCallAttempts() != null ? entity.getCallAttempts() : 0) + 1);
        entity.setLastCalledAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        callbackTaskMapper.updateById(entity);
        log.info("[CallbackTaskService] 漏话任务发起回呼: id={}, customer={}", id, entity.getCustomerNumber());
    }

    private CallbackTaskVO buildCallbackVO(CallbackTaskEntity entity) {
        String durationStr = "0秒";
        if (entity.getWaitDurationMs() != null) {
            long totalSec = entity.getWaitDurationMs() / 1000;
            if (totalSec >= 60) {
                long min = totalSec / 60;
                long sec = totalSec % 60;
                durationStr = min + "分" + (sec > 0 ? sec + "秒" : "");
            } else {
                durationStr = totalSec + "秒";
            }
        }

        return CallbackTaskVO.builder()
                .id(String.valueOf(entity.getId()))
                .phone(entity.getCustomerNumber())
                .time(entity.getMissedAt() != null ? entity.getMissedAt().format(TIME_FMT) : "")
                .reason(entity.getMissedReason() != null ? entity.getMissedReason() : "排队放弃")
                .duration(durationStr)
                .status(entity.getStatus())
                .assignee(entity.getAssigneeName())
                .assigneeWorkNo(entity.getAssigneeWorkNo())
                .callAttempts(entity.getCallAttempts() != null ? entity.getCallAttempts() : 0)
                .lastCalledAt(entity.getLastCalledAt() != null ? entity.getLastCalledAt().format(TIME_FMT) : null)
                .notes(entity.getNotes())
                .build();
    }
}
