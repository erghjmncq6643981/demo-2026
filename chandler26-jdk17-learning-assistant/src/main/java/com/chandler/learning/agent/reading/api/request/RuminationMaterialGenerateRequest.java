package com.chandler.learning.agent.reading.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 编织场景材料文章请求。
 */
@Data
@Schema(name = "编织场景材料文章请求")
public class RuminationMaterialGenerateRequest {

    @NotNull(message = "单词本 ID 不能为空")
    @Schema(description = "个人单词本 ID")
    private Long wordbookId;

    @NotBlank(message = "场景编码不能为空")
    @Schema(description = "主题场景编码")
    private String scenarioCode;

    @NotEmpty(message = "词袋中目标词汇至少需要 5 个")
    @Schema(description = "本次反刍选中的目标词汇列表")
    private List<String> targetWords;

    @Schema(description = "文章难度：easy、medium、hard")
    private String difficulty;

    @Schema(description = "字数范围：150-200、300-500")
    private String wordCountRange;

    @Schema(description = "客户端幂等提交标识，同一提交重发时用于复用已创建异步任务")
    private String requestId;
}
