package com.chandler.fcc.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 坐席话务弹屏载荷 DTO
 * <p>
 * 只承载<b>可被真实事实支撑</b>的字段，分三类来源：
 * </p>
 * <ol>
 *   <li><b>呼叫信令事实</b>：来自 NATS 通道事件驱动的通话上下文 (call_id / 主被叫 / 方向)；</li>
 *   <li><b>配置事实</b>：来自 {@code fcc_flow_definition}、{@code fcc_did_number}、{@code fcc_agent}；</li>
 *   <li><b>历史事实</b>：来自 {@code fcc_call_session} 与 {@code fcc_call_recording} 中同一号码的前序通话。</li>
 * </ol>
 * <p>
 * 设计约束：<b>取不到真实值一律留空</b>，绝不以示例数据填充。前端对空值展示中性占位，
 * 从而不会出现"看起来有客户档案、实际是编造数据"的假象。
 * </p>
 *
 * @author Chandler
 * @version 2.0.0
 * @since 2026-09-19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "坐席话务弹屏载荷 (全部字段均来自真实事实，无值即留空)")
public class IncomingScreenPopDTO implements Serializable {

    private static final long serialVersionUID = 2L;

    // ================= 1. 呼叫信令事实 =================

    @Schema(description = "业务通话唯一标识 (FCC call_id，全生命周期稳定)", example = "94296998894768128")
    private String callId;

    @Schema(description = "呼叫方向 (INBOUND 呼入 / OUTBOUND 呼出)", example = "INBOUND")
    private String direction;

    @Schema(description = "客户侧号码：呼入为主叫号码，呼出为被叫号码", example = "19166340294")
    private String callerNumber;

    @Schema(description = "接入线路号码：呼入为热线 DID，呼出为坐席外呼主叫号", example = "021-60882100")
    private String didNumber;

    @Schema(description = "振铃超时秒数 (取自实际下发 Dial 的 timeout)", example = "30")
    private Integer ringTimeoutSeconds;

    // ================= 2. 路由与流程事实 =================

    @Schema(description = "命中的话务流程名称 (fcc_flow_definition.flow_name)", example = "呼入客服流程")
    private String flowName;

    @Schema(description = "真实流转轨迹：IVR 按键 + 命中的流程", example = "按键[1] → 呼入客服流程")
    private String ivrPath;

    @Schema(description = "真实路由依据", example = "IVR 按键[1] 分流至坐席 90101 · 终端 1007")
    private String routingReason;

    // ================= 3. 客户信息 (仅外呼时由坐席录入，无 CRM 事实则为空) =================

    @Schema(description = "客户姓名 (仅坐席外呼时录入值)", example = "张建国")
    private String customerName;

    @Schema(description = "客户单位 (仅坐席外呼时录入值)", example = "深圳港供应链物流有限公司")
    private String companyName;

    // ================= 4. 同号码前序通话事实 (防撞单) =================

    @Schema(description = "前序接待坐席姓名 (fcc_call_session.agent_name)", example = "陈松")
    private String lastAgentName;

    @Schema(description = "前序接待坐席工号 (fcc_call_session.agent_work_no)", example = "901002")
    private String lastAgentWorkNo;

    @Schema(description = "前序通话开始时间 (UTC)", example = "2026-09-18T08:45:12")
    private String lastCallTime;

    @Schema(description = "前序通话摘要：时长 + 挂机结果", example = "通话 04:12 · NORMAL_CLEARING")
    private String lastCallSummary;

    @Schema(description = "前序通话录音复播地址 (仅当录音元数据存在时给出)",
            example = "/api/admin/recordings/94653312724504576/stream")
    private String lastRecordingUrl;
}
