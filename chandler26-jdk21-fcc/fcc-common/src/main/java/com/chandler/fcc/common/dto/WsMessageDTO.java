package com.chandler.fcc.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * WebSocket 统一消息传输封套 DTO
 *
 * @author Chandler
 * @version 1.0.0
 * @since 2026-09-18
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "WebSocket 统一消息传输封套")
public class WsMessageDTO<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "消息类型，参考 WsMessageTypeEnum", example = "SCREEN_POP")
    private String type;

    @Schema(description = "目标坐席工号", example = "901001")
    private String workNo;

    @Schema(description = "呼叫会话标识", example = "call-94296998894768128")
    private String callId;

    @Schema(description = "业务载荷数据")
    private T data;

    @Schema(description = "时间戳毫秒数", example = "1758172800000")
    private Long timestamp;

    @Schema(description = "追踪链路跟踪标识", example = "trace-ws-8912")
    private String traceId;

    /**
     * 快速构建响应消息
     *
     * @param type 消息类型
     * @param workNo 坐席工号
     * @param callId 呼叫ID
     * @param data 载荷数据
     * @param <T> 数据泛型
     * @return WsMessageDTO 实例
     */
    public static <T> WsMessageDTO<T> of(String type, String workNo, String callId, T data) {
        return WsMessageDTO.<T>builder()
                .type(type)
                .workNo(workNo)
                .callId(callId)
                .data(data)
                .timestamp(System.currentTimeMillis())
                .traceId("ws-" + System.currentTimeMillis())
                .build();
    }
}
