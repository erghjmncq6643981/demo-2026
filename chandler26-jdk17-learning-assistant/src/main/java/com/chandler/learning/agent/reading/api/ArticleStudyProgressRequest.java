package com.chandler.learning.agent.reading.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 语境精读阶段更新请求。
 */
@Data
@Schema(name = "语境精读阶段更新请求")
public class ArticleStudyProgressRequest {

    @NotBlank(message = "学习阶段不能为空")
    @Schema(description = "当前阶段：reading、vocabulary、check")
    private String stage;
}
