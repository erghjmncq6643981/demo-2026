package com.chandler.learning.agent.vocabulary.domain.bo;

import com.chandler.learning.agent.vocabulary.domain.entity.LearningWordbookEntry;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 单词本列表投影。词卡 JSON 不参与列表查询，仅由 SQL 提取首个音标和释义摘要。
 */
@Data
public class WordbookEntrySummaryItem {

    /** 主键。 */
    private Long id;
    /** 所属单词本。 */
    private Long wordbookId;
    /** 公共词卡缓存标识。 */
    private Long vocabularyId;
    /** 展示词汇。 */
    private String term;
    /** 归一化词汇。 */
    private String normalizedTerm;
    /** 首个音标摘要。 */
    private String phonetic;
    /** 首个释义摘要。 */
    private String meaningText;
    /** 学习状态。 */
    private String status;
    /** 复习阶段。 */
    private Integer reviewStage;
    /** 掌握度。 */
    private Integer masteryScore;
    /** 最近复习时间。 */
    private LocalDateTime lastReviewTime;
    /** 下次复习时间。 */
    private LocalDateTime nextReviewTime;
    /** 复习次数。 */
    private Integer reviewCount;
    /** 答对次数。 */
    private Integer correctCount;
    /** 答错次数。 */
    private Integer wrongCount;
    /** 词卡状态。 */
    private String cardStatus;
    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 转换为可复用的领域实体，便于现有响应装配器统一处理学习状态。 */
    public LearningWordbookEntry toEntity() {
        LearningWordbookEntry entry = new LearningWordbookEntry();
        entry.setId(id);
        entry.setWordbookId(wordbookId);
        entry.setVocabularyId(vocabularyId);
        entry.setTerm(term);
        entry.setNormalizedTerm(normalizedTerm);
        entry.setStatus(status);
        entry.setReviewStage(reviewStage);
        entry.setMasteryScore(masteryScore);
        entry.setLastReviewTime(lastReviewTime);
        entry.setNextReviewTime(nextReviewTime);
        entry.setReviewCount(reviewCount);
        entry.setCorrectCount(correctCount);
        entry.setWrongCount(wrongCount);
        entry.setCardStatus(cardStatus);
        entry.setCreateTime(createTime);
        return entry;
    }
}
