package com.chandler.learning.agent.system.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chandler.learning.agent.common.persistence.BaseEntity;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统日志可靠投递 Outbox。
 * <p>
 * 与业务事务一同写入，提交后由异步消费者转存到用户可见的 {@code learning_system_log}。
 */
@Data
@TableName("learning_system_log_outbox")
public class LearningSystemLogOutbox extends BaseEntity {

    /** 事件 ID，同时作为最终系统日志主键保证幂等写入。 */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 日志归属用户 ID。 */
    private Long userId;

    /** 日志类型。 */
    private String logType;

    /** 业务可读标题。 */
    private String title;

    /** 有边界的日志详情。 */
    private String detail;

    /** 日志来源。 */
    private String source;

    /** 关联业务类型。 */
    private String businessType;

    /** 关联业务 ID。 */
    private String businessId;

    /** 原始业务动作发生时间。 */
    private LocalDateTime occurredAt;

    /** 请求链路追踪标识。 */
    private String traceId;

    /** 投递状态：pending、processing、succeeded。 */
    private String status;

    /** 批量领取令牌。 */
    private String claimToken;

    /** 成功写入最终日志表的时间。 */
    private LocalDateTime processedTime;
}
