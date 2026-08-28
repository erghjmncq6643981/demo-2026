package com.chandler.learning.agent.reading.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 语境精读检测答案。
 */
@Data
@Schema(name = "语境精读检测答案")
public class ArticleStudyAnswerRequest {

    @NotNull(message = "题号不能为空")
    @Schema(description = "题目下标，从 0 开始")
    private Integer questionIndex;

    @NotBlank(message = "答案不能为空")
    @Schema(description = "学习者提交的答案")
    private String answer;
}
