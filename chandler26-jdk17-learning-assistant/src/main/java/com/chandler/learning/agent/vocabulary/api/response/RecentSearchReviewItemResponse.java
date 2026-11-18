package com.chandler.learning.agent.vocabulary.api.response;

import com.fasterxml.jackson.annotation.JsonRawValue;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 近期查词轻量复习词条响应。
 */
@Data
@Schema(description = "近期查词轻量复习词条响应")
public class RecentSearchReviewItemResponse {

    @Schema(description = "复习条目唯一标识（优先词条 ID，否则为查词足迹 ID）")
    private Long id;

    @Schema(description = "查词足迹记录 ID（兜底来源时可为空）")
    private Long lookupId;

    @Schema(description = "关联的个人单词本词条 ID（未加入单词本时为空）")
    private Long entryId;

    @Schema(description = "所属个人单词本 ID（未加入单词本时为空）")
    private Long wordbookId;

    @Schema(description = "关联公共词卡缓存 ID")
    private Long vocabularyId;

    @Schema(description = "展示英文单词或短语")
    private String term;

    @Schema(description = "归一化单词或短语")
    private String normalizedTerm;

    @Schema(description = "查询来源：study-卡片查词，quick_lookup-快捷查词，wordbook_add-手动加词，wordbook_recent-最近词条兜底")
    private String lookupSource;

    @Schema(description = "累计查询次数")
    private Integer lookupCount;

    @Schema(description = "最近一次查询时间")
    private LocalDateTime lastLookupTime;

    @Schema(description = "最近一次复习过关时间")
    private LocalDateTime lastReviewedTime;

    @Schema(description = "当前掌握状态：familiar、vague、forgotten")
    private String status;

    @Schema(description = "个人学习笔记")
    private String note;

    @JsonRawValue
    @Schema(description = "结构化词卡 JSON（含音标、释义、认知画像、例句与记忆提示）")
    private String parsed;
}
