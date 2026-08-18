package com.chandler.learning.agent.domain.dto.learning;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.Data;

import java.util.List;

/**
 * 语境精读完成请求。
 */
@Data
@Schema(name = "语境精读完成请求")
public class ArticleStudyCompleteRequest {

    @Valid
    @Schema(description = "按题号提交的阅读检测答案")
    private List<ArticleStudyAnswerRequest> answers;
}
