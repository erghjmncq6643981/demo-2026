package com.chandler.fcc.admin.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 通话历史话单 CDR 聚合展示视图对象 VO
 *
 * @author Chandler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "通话历史话单详情视图")
public class CallCdrVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "通话会话 ID", example = "71827391823719283")
    private Long id;

    @Schema(description = "流程控制标识 ctrl_id", example = "ctrl-20260918-001")
    private String ctrlId;

    @Schema(description = "业务订单号", example = "ORDER-88273")
    private String bizId;

    @Schema(description = "呼叫模式", example = "INBOUND_CUSTOMER_SERVICE")
    private String modelType;

    @Schema(description = "关联的通话流程编码", example = "FLOW-INBOUND")
    private String flowCode;

    @Schema(description = "目标类型 (AGENT, GROUP)", example = "AGENT")
    private String routeTargetType;

    @Schema(description = "目标标识 (工号或技能组代码)", example = "901001")
    private String routeTargetId;

    @Schema(description = "呼叫方向 (INBOUND, OUTBOUND, INTERNAL)", example = "INBOUND")
    private String direction;

    @Schema(description = "主叫号码", example = "13800000000")
    private String caller;

    @Schema(description = "主叫客户姓名", example = "张闯")
    private String callerName;

    @Schema(description = "运营商", example = "中国移动")
    private String carrier;

    @Schema(description = "被叫号码", example = "1001")
    private String callee;

    @Schema(description = "DID 接入号", example = "01088889999")
    private String didNumber;

    @Schema(description = "通话结果状态 (ANSWERED: 已接听, NO_ANSWER: 未接听)", example = "ANSWERED")
    private String status;

    @Schema(description = "响应类型 (ANSWER: 已接听, MISSED: 未接听)", example = "ANSWER")
    private String answerType;

    @Schema(description = "挂机释放原因", example = "NORMAL_CLEARING")
    private String hangupCause;

    @Schema(description = "服务坐席工号", example = "901001")
    private String agentWorkNo;

    @Schema(description = "服务坐席姓名", example = "钱丁君")
    private String agentName;

    @Schema(description = "坐席分机号", example = "1001")
    private String extension;

    @Schema(description = "排队等待时长 (毫秒)", example = "4200")
    private Long waitDurationMs;

    @Schema(description = "双方通话交谈时长 (毫秒)", example = "68000")
    private Long talkDurationMs;

    @Schema(description = "录音通话展示时长 (如 01:14 或 00:20)", example = "01:14")
    private String audioDuration;

    @Schema(description = "呼叫总时长 (毫秒)", example = "72200")
    private Long totalDurationMs;

    @Schema(description = "满意度按键评分 (如 1-5分)", example = "5")
    private Integer evaluationScore;

    @Schema(description = "满意度明细", example = "{\"score\":5}")
    private String evaluationDetails;

    @Schema(description = "录音复播地址 (支持 HTTP Range 拖拽，本地共享存储时走管理端流式代理)",
            example = "/api/admin/recordings/by-rec-id/rec-call-123/stream")
    private String recordingUrl;

    @Schema(description = "录音下载地址 (Content-Disposition: attachment)",
            example = "/api/admin/recordings/by-rec-id/rec-call-123/download")
    private String recordingDownloadUrl;

    @Schema(description = "录音业务唯一标识", example = "rec-call-123")
    private String recordingId;

    @Schema(description = "呼叫发起时间")
    private LocalDateTime initiatedAt;

    @Schema(description = "应答接通时间")
    private LocalDateTime answeredAt;

    @Schema(description = "通话挂机结束时间")
    private LocalDateTime endedAt;

    @Schema(description = "通话分段 Leg 列表")
    private List<CallLegVO> legs;

}
