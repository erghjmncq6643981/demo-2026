package com.chandler.learning.agent.vocabulary.api.request;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

/**
 * ReviewSubmitRequest 类。
 */
@Data
public class ReviewSubmitRequest {

    @Schema(description = "执行结果")
    private String result;

    @Schema(description = "得分")
    private Integer score;

    @Schema(description = "耗时（秒）")
    private Integer durationSeconds;

    @Schema(description = "关联业务标识")
    private Long wordProgressId;

    @Schema(description = "关联业务标识")
    private Long planId;

    @Schema(description = "场景单元标识")
    private Long unitId;

    @Schema(description = "检查类型")
    private String assessmentType;

    @Schema(description = "题目 JSON")
    private String questionJson;

    @Schema(description = "文本答案")
    private String answerText;

    @Schema(description = "正确答案")
    private String correctAnswer;

    @Schema(description = "检查结果")
    private String checkResult;

    @Schema(description = "拼写准确率")
    private Double typingAccuracy;

    @Schema(description = "提示等级")
    private Integer hintLevel;

    @Schema(description = "尝试次数")
    private Integer attemptCount;

    @Schema(description = "耗时（毫秒）")
    private Long durationMillis;
}
