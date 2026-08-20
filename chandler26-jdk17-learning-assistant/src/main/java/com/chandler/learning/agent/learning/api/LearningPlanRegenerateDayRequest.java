package com.chandler.learning.agent.learning.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 重新生成指定日期场景材料请求。
 */
@Data
@Schema(description = "重新生成指定日期场景材料请求")
public class LearningPlanRegenerateDayRequest {

    @Schema(description = "AI 模型配置 ID，缺省时使用系统默认模型")
    private Long modelConfigId;

    @NotNull(message = "指定重新生成的推荐日期不能为空")
    @Schema(description = "指定重新生成的推荐日期", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate recommendedDate;
}
