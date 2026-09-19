package com.chandler.fcc.admin.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 通话信道 Leg 详情视图对象 VO
 *
 * @author Chandler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "通话信道Leg详情视图")
public class CallLegVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Leg 主键 ID", example = "10001")
    private Long id;

    @Schema(description = "FreeSWITCH Channel UUID", example = "c8df0b57-3a1b-4171-a0ea-7323b7e7161b")
    private String legUuid;

    @Schema(description = "Leg 类型 (CALLER, CALLEE, CONSULT)", example = "CALLER")
    private String legType;

    @Schema(description = "本端号码", example = "13800000000")
    private String fromNumber;

    @Schema(description = "对端号码", example = "1001")
    private String toNumber;

    @Schema(description = "终端类型 (SIP, WEBRTC, TRUNK)", example = "SIP")
    private String endpointType;

    @Schema(description = "Leg 终态状态", example = "HANGUP")
    private String status;

    @Schema(description = "振铃时长 (毫秒)", example = "3500")
    private Long ringDurationMs;

    @Schema(description = "通话时长 (毫秒)", example = "45000")
    private Long billDurationMs;

    @Schema(description = "接收编解码", example = "PCMA")
    private String readCodec;

    @Schema(description = "发送编解码", example = "PCMA")
    private String writeCodec;
}
