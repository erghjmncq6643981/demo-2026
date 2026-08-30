package com.chandler.learning.agent.learning.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 场景词汇检查结果。
 */
@Data
public class LearningAssessmentSubmitResponse {

    @Schema(description = "场景单元词条 ID")
    private Long unitEntryId;

    @Schema(description = "检查类型")
    private String assessmentType;

    @Schema(description = "是否正确")
    private Boolean correct;

    @Schema(description = "正确答案")
    private String correctAnswer;

    @Schema(description = "拼写准确率")
    private Double typingAccuracy;

    @Schema(description = "学习状态")
    private String learningState;

    @Schema(description = "词义识别得分")
    private Integer recognitionScore;

    @Schema(description = "拼写得分")
    private Integer spellingScore;

    @Schema(description = "已完成挑战的核心词数量")
    private Integer completedCoreCount;

    @Schema(description = "核心词汇数量")
    private Integer coreWordCount;

    @Schema(description = "场景单元是否满足完成条件")
    private Boolean unitReadyToComplete;

    @Schema(description = "下次复习时间")
    private LocalDateTime nextReviewTime;
}
