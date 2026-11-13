package com.chandler.learning.agent.reading.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 微场景探针作答提交请求。
 */
@Data
@Schema(name = "微场景探针作答提交请求")
public class RuminationProbeSubmitRequest {

    @Schema(description = "关联的反刍文章记录 ID，可选")
    private Long recordId;

    @NotNull(message = "单词本不能为空")
    @Schema(description = "当前单词本 ID")
    private Long wordbookId;

    @NotNull(message = "探针题目 ID 不能为空")
    @Schema(description = "微环境探针题目 ID")
    private Long probeId;

    @Schema(description = "考察的目标单词拼写，可选校验")
    private String targetWord;

    @Schema(description = "探针序号，如 1、2、3")
    private Integer probeIndex;

    @NotBlank(message = "学习者选中的答案选项不能为空")
    @Schema(description = "学习者选中的答案选项文本")
    private String selectedAnswer;

    @Schema(description = "正确答案选项文本（已弃用：由服务端独立判定，客户端传入将被忽略）")
    private String correctAnswer;

    @Schema(description = "是否回答正确（已弃用：由服务端独立判定，客户端传入将被忽略）")
    private Boolean isCorrect;

    @Schema(description = "作答耗时（毫秒）")
    private Long durationMillis;
}
