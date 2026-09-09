package com.chandler.learning.agent.learning.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.chandler.learning.agent.common.persistence.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户学习活动原始事件 DO。
 * <p>
 * 事件只追加不修改，status 用于异步投影到日汇总表，失败时可恢复重试。
 */
@Data
@TableName("learning_activity_event")
@Schema(name = "学习活动事件")
public class LearningActivityEvent extends BaseEntity {

    /** 活动事件主键。 */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 活动所属用户 ID。 */
    private Long userId;

    /** 活动类型编码。 */
    private String eventType;

    /** 用户行为实际发生时间。 */
    private LocalDateTime occurredAt;

    /** 关联学习计划 ID。 */
    private Long planId;

    /** 关联场景单元 ID。 */
    private Long unitId;

    /** 关联场景材料或文章记录 ID。 */
    private Long materialId;

    /** 关联个人单词本词条 ID。 */
    private Long entryId;

    /** 本次行为涉及的数量。 */
    private Integer quantity;

    /** 本次行为有效时长，单位秒。 */
    private Integer durationSeconds;

    /** 行为结果编码，例如 correct、incorrect。 */
    private String resultCode;

    /** 业务幂等键，防止网络重试重复累计。 */
    private String idempotencyKey;

    /** 投影状态：pending、processing、succeeded。 */
    private String status;

    /** 批量投影领取令牌。 */
    private String claimToken;

    /** 投影成功时间。 */
    private LocalDateTime processedTime;

    /** 创建活动事件。 */
    public static LearningActivityEvent create(Long userId, String eventType, LocalDateTime occurredAt,
                                               Long planId, Long unitId, Long materialId, Long entryId,
                                               Integer quantity, Integer durationSeconds, String resultCode,
                                               String idempotencyKey) {
        LearningActivityEvent event = new LearningActivityEvent();
        // 事件使用自定义 XML 插入，不会触发 BaseMapper 的 ASSIGN_ID，因此必须显式生成主键。
        event.setId(IdWorker.getId());
        event.setUserId(userId);
        event.setEventType(eventType);
        event.setOccurredAt(occurredAt);
        event.setPlanId(planId);
        event.setUnitId(unitId);
        event.setMaterialId(materialId);
        event.setEntryId(entryId);
        event.setQuantity(quantity == null || quantity < 1 ? 1 : quantity);
        event.setDurationSeconds(durationSeconds);
        event.setResultCode(resultCode);
        event.setIdempotencyKey(idempotencyKey);
        event.setStatus(com.chandler.learning.agent.learning.domain.constant.LearningActivityConstants.EVENT_PENDING);
        event.setDeleted(false);
        event.setVersion(0);
        event.setCreateBy(userId);
        event.setUpdateBy(userId);
        event.setCreateTime(occurredAt);
        event.setUpdateTime(occurredAt);
        return event;
    }
}
