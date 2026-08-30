package com.chandler.learning.agent.vocabulary.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 个人单词本响应数据。
 */
@Data
public class WordbookEntryResponse {

    @Schema(description = "主键 ID")
    private Long id;

    @Schema(description = "单词本标识")
    private Long wordbookId;

    @Schema(description = "词汇标识")
    private Long vocabularyId;

    @Schema(description = "词汇学习进度 ID")
    private Long progressId;

    @Schema(description = "公共词本词条 ID")
    private Long catalogEntryId;

    @Schema(description = "英文词汇")
    private String term;

    @Schema(description = "标准化词汇")
    private String normalizedTerm;

    @Schema(description = "学习笔记")
    private String note;

    @Schema(description = "当前业务状态")
    private String status;

    @Schema(description = "复习阶段")
    private Integer reviewStage;

    @Schema(description = "掌握分数")
    private Integer masteryScore;

    @Schema(description = "最近复习时间")
    private LocalDateTime lastReviewTime;

    @Schema(description = "下次复习时间")
    private LocalDateTime nextReviewTime;

    @Schema(description = "复习次数")
    private Integer reviewCount;

    @Schema(description = "答对次数")
    private Integer correctCount;

    @Schema(description = "答错次数")
    private Integer wrongCount;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @com.fasterxml.jackson.annotation.JsonRawValue
    @Schema(description = "解析后的结构化内容")
    private String parsed;

    @Schema(description = "词卡快照模型供应商")
    private String snapshotProvider;

    @Schema(description = "词卡快照模型名称")
    private String snapshotModelName;

    @Schema(description = "词卡快照会话 ID")
    private Long snapshotSessionId;

    @Schema(description = "快照时间")
    private LocalDateTime snapshotTime;

    @Schema(description = "词卡生成状态")
    private String cardStatus;

    @Schema(description = "词卡生成失败原因")
    private String cardErrorMessage;

    @Schema(description = "词卡生成完成时间")
    private LocalDateTime cardGeneratedTime;

    @Schema(description = "标签列表")
    private List<VocabularyTagResponse> tags;

    @Schema(description = "词汇关联列表")
    private List<VocabularyRelationResponse> relations;
}
