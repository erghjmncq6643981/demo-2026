package com.chandler.learning.agent.reading.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 从指定历史快照版本恢复知识反刍场景星系图谱请求。
 */
@Data
@Schema(name = "恢复知识反刍场景图谱历史版本请求")
public class RuminationGraphHistoryRestoreRequest {

    @NotNull(message = "单词本 ID 不能为空")
    @Schema(description = "个人单词本 ID")
    private Long wordbookId;

    @NotBlank(message = "场景编码不能为空")
    @Schema(description = "主题场景编码")
    private String scenarioCode;

    @NotNull(message = "历史版本记录 ID 不能为空")
    @Schema(description = "目标历史快照记录主键 ID")
    private Long historyId;
}
