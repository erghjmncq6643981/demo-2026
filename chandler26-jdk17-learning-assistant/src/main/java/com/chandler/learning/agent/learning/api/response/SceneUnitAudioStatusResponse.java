package com.chandler.learning.agent.learning.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 场景单元音频及异步生成任务状态响应。
 */
@Data
public class SceneUnitAudioStatusResponse {

    @Schema(description = "场景单元主键 ID")
    private Long unitId;

    @Schema(description = "音频文件是否已在服务器就绪")
    private Boolean hasAudio;

    @Schema(description = "异步任务状态：none、pending、running、completed、failed")
    private String taskStatus;

    @Schema(description = "关联的 AI 异步任务 ID")
    private Long taskId;

    @Schema(description = "任务执行失败信息")
    private String errorMessage;
}
