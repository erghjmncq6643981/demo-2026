package com.chandler.learning.agent.vocabulary.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 近期查词轻量复习队列响应。
 */
@Data
@Schema(description = "近期查词轻量复习队列响应")
public class RecentSearchReviewQueueResponse {

    @Schema(description = "队列命中模式：today_yesterday-今天与昨天查词，recent_fallback-近两日不足5词兜底最近20词，wordbook_fallback-无查词记录时兜底最近词条")
    private String mode;

    @Schema(description = "今天与昨天累计查过的词数")
    private Integer recentTwoDayCount;

    @Schema(description = "本轮返回的复习词条总数")
    private Integer totalCount;

    @Schema(description = "展示给学习者的中文场景提示")
    private String hintMessage;

    @Schema(description = "近期查词复习队列列表")
    private List<RecentSearchReviewItemResponse> items;
}
