package com.chandler.learning.agent.reading.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识反刍微环境探针响应。
 */
@Data
@Schema(name = "微环境探针响应")
public class RuminationProbeResponse {

    @Schema(description = "探针题目主键 ID（作为字符串传输）")
    private String id;

    @Schema(description = "关联场景材料文章 ID")
    private String materialId;

    @Schema(description = "考察核心目标词")
    private String targetWord;

    @Schema(description = "探针题型标识")
    private String probeType;

    @Schema(description = "探针题型中文名（如：情境辨析题、动作搭配题、因果填空题）")
    private String probeTypeName;

    @Schema(description = "情境题干描述")
    private String question;

    @Schema(description = "单选选项列表")
    private List<String> options;

    @Schema(description = "正确选项文本")
    private String correctAnswer;

    @Schema(description = "情境线索提示")
    private String hint;

    @Schema(description = "题目生成来源：ai 默认大模型生成，fallback 本地语义引擎补齐或降级")
    private String generationSource;

    @Schema(description = "用户选择答案")
    private String selectedAnswer;

    @Schema(description = "作答是否正确")
    private Boolean isCorrect;

    @Schema(description = "获得积分")
    private Integer score;

    @Schema(description = "星系网络是否已点亮")
    private Boolean illuminated;

    @Schema(description = "作答时间")
    private LocalDateTime answeredTime;
}
