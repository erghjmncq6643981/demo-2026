package com.chandler.learning.agent.reading.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 知识反刍场景材料 AI 语音合成与就绪状态响应。
 */
@Data
@Schema(name = "场景材料音频状态响应")
public class RuminationMaterialAudioStatusResponse {

    @Schema(description = "场景材料主键 ID（作为字符串传输）")
    private String materialId;

    @Schema(description = "音频文件是否已在服务器就绪")
    private Boolean hasAudio;

    @Schema(description = "音频合成状态：none、pending、running、completed、failed")
    private String audioStatus;

    @Schema(description = "异步任务状态：none、pending、running、completed、failed")
    private String taskStatus;

    @Schema(description = "关联的 AI 异步任务 ID（作为字符串传输）")
    private String taskId;

    @Schema(description = "任务执行失败原因")
    private String errorMessage;
}
