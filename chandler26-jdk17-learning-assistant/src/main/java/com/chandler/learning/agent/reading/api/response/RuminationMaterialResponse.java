package com.chandler.learning.agent.reading.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识反刍场景材料文章响应。
 */
@Data
@Schema(name = "场景材料文章响应")
public class RuminationMaterialResponse {

    @Schema(description = "材料主键 ID（作为字符串传输）")
    private String id;

    @Schema(description = "个人单词本 ID")
    private String wordbookId;

    @Schema(description = "主题场景编码")
    private String scenarioCode;

    @Schema(description = "场景文章标题")
    private String title;

    @Schema(description = "场景背景描述")
    private String scenarioSummary;

    @Schema(description = "英文原文正文")
    private String articleContent;

    @Schema(description = "中文对照翻译")
    private String translationContent;

    @Schema(description = "本次反刍所选目标词汇列表")
    private List<String> targetWords;

    @Schema(description = "正文字数")
    private Integer wordCount;

    @Schema(description = "内容生成来源：ai 默认大模型生成，fallback 本地语义引擎降级")
    private String generationSource;

    @Schema(description = "关联异步生成任务 ID（作为字符串传输）")
    private String taskId;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "关联的微环境探针题目列表")
    private List<RuminationProbeResponse> probes;

    @Schema(description = "场景材料分句列表与句子巩固进度（用于按句高亮与阅读后跟敲练习）")
    private List<RuminationPracticeSentenceResponse> practiceSentences;

    @Schema(description = "当前场景材料句子巩固汇总统计")
    private RuminationPracticeSummaryResponse practiceSummary;

    @Schema(description = "材料结算扣除词袋后的星系图谱版本号")
    private Integer graphVersion;

    @Schema(description = "本次生成是否扣除了词袋词汇（重新生成历史材料时不扣除）")
    private Boolean bagConsumed;

    @Schema(description = "是否已生成 AI 真人朗读音频")
    private Boolean hasAudio;

    @Schema(description = "AI 语音合成状态（none/pending/running/completed/failed）")
    private String audioStatus;
}
