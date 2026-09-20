package com.chandler.fcc.admin.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * 漏话待办任务与回拨总池持久化实体 (fcc_callback_task)
 * <p>
 * 记录客户排队超时、坐席忙或未接起的漏话待办，支持派单与优先回呼闭环。
 * </p>
 *
 * @author Chandler
 */
@TableName("fcc_callback_task")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@SuperBuilder
public class CallbackTaskEntity extends BaseEntity {

    /**
     * 关联原未接通通话 ID (fcc_call_session.id)
     */
    private Long sourceCallId;

    /**
     * 客户手机号码
     */
    private String customerNumber;

    /**
     * 呼入进线 DID 号码
     */
    private String didNumber;

    /**
     * 漏话发生时间
     */
    private LocalDateTime missedAt;

    /**
     * 漏话原因 (坐席忙未接起放弃 / 排队等待超时 / 技能组溢出 / 客户主动挂断)
     */
    private String missedReason;

    /**
     * 客户等待时长 (毫秒)
     */
    private Long waitDurationMs;

    /**
     * 任务状态 (PENDING-待办, ASSIGNED-已派单, CALLED-已呼出, COMPLETED-已回访, CANCELLED-已取消)
     */
    private String status;

    /**
     * 优先级 (0 默认, 数值越大越优先)
     */
    private Integer priority;

    /**
     * 指派坐席主键 ID
     */
    private Long assigneeAgentId;

    /**
     * 指派跟进坐席工号
     */
    private String assigneeWorkNo;

    /**
     * 指派跟进坐席姓名
     */
    private String assigneeName;

    /**
     * 回访呼叫尝试次数
     */
    private Integer callAttempts;

    /**
     * 最后一次回拨时间
     */
    private LocalDateTime lastCalledAt;

    /**
     * 跟进备注说明
     */
    private String notes;
}
