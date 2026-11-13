package com.chandler.learning.agent.reading.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 微场景探针作答提交响应。
 */
@Data
@Schema(name = "微场景探针作答提交响应")
public class RuminationProbeSubmitResponse {

    @Schema(description = "考察目标单词")
    private String targetWord;

    @Schema(description = "单词本词条 ID，若已在词本中")
    private Long entryId;

    @Schema(description = "服务端独立判定的作答正确性")
    private Boolean isCorrect;

    @Schema(description = "探针题目的标准正确答案")
    private String correctAnswer;

    @Schema(description = "本次获得的熟练度加分")
    private Integer scoreGained;

    @Schema(description = "更新后的掌握度分值（0-100）")
    private Integer masteryScoreAfter;

    @Schema(description = "更新后的艾宾浩斯复习阶段")
    private Integer reviewStageAfter;

    @Schema(description = "推迟后的下一次复习时间")
    private LocalDateTime nextReviewTime;

    @Schema(description = "是否在星系图谱中触发点亮高光反馈")
    private Boolean illuminated;

    @Schema(description = "是否为重复提交的幂等返回（重复提交不会二次增加熟练度）")
    private Boolean alreadyAnswered;
}
