package com.chandler.learning.agent.domain.entity.learning;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chandler.learning.agent.domain.entity.BaseEntity;
import com.chandler.learning.agent.domain.entity.vocabulary.EnglishVocabularyStudyRecord;
import com.chandler.learning.agent.support.LearningConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 单词本词条 DO。
 * <p>
 * 保存用户把公共 AI 词汇缓存加入个人单词本后的私人学习状态和学习卡快照。
 */
@Data
@TableName("learning_wordbook_entry")
public class LearningWordbookEntry extends BaseEntity {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "词条所属用户 ID")
    private Long userId;

    @Schema(description = "词条所在单词本 ID")
    private Long wordbookId;

    @Schema(description = "公共英语词汇学习缓存 ID")
    private Long vocabularyId;

    @Schema(description = "展示单词或短语，保留 AI 标准化后的大小写")
    private String term;

    @Schema(description = "归一化单词或短语，用于去重和查询")
    private String normalizedTerm;

    @Schema(description = "用户 Markdown 学习笔记")
    private String note;

    @Schema(description = "加入单词本时冻结的 AI 原始回复快照，避免公共缓存刷新影响个人详情")
    private String snapshotRawContent;

    @Schema(description = "加入单词本时冻结的结构化学习卡 JSON 快照")
    private String snapshotParsedJson;

    @Schema(description = "加入单词本时冻结的标签 JSON 快照")
    private String snapshotTagsJson;

    @Schema(description = "加入单词本时冻结的关联词 JSON 快照")
    private String snapshotRelationsJson;

    @Schema(description = "快照使用的模型供应商")
    private String snapshotProvider;

    @Schema(description = "快照使用的模型名称")
    private String snapshotModelName;

    @Schema(description = "快照关联的 AI 会话 ID")
    private Long snapshotSessionId;

    @Schema(description = "快照生成时间")
    private LocalDateTime snapshotTime;

    @Schema(description = "熟练状态：familiar-熟悉，forgotten-遗忘，vague-模糊")
    private String status;

    @Schema(description = "艾宾浩斯复习阶段，决定下一次复习间隔")
    private Integer reviewStage;

    @Schema(description = "掌握度 0-100")
    private Integer masteryScore;

    @Schema(description = "首次复习时间")
    private LocalDateTime firstReviewTime;

    @Schema(description = "最近复习时间")
    private LocalDateTime lastReviewTime;

    @Schema(description = "下次复习时间")
    private LocalDateTime nextReviewTime;

    @Schema(description = "进入复习队列次数")
    private Integer dueCount;

    @Schema(description = "复习次数")
    private Integer reviewCount;

    @Schema(description = "记住次数")
    private Integer correctCount;

    @Schema(description = "忘记次数")
    private Integer wrongCount;

    public static LearningWordbookEntry createNew(Long userId, Long wordbookId, EnglishVocabularyStudyRecord vocabulary,
                                                  String note, LocalDateTime now) {
        LearningWordbookEntry entry = new LearningWordbookEntry();
        entry.setUserId(userId);
        entry.setWordbookId(wordbookId);
        entry.setVocabularyId(vocabulary.getId());
        entry.setTerm(vocabulary.getTerm());
        entry.setNormalizedTerm(vocabulary.getNormalizedTerm());
        entry.setNote(note);
        entry.applyVocabularySnapshot(vocabulary, now, null, null);
        entry.setStatus(LearningConstants.Review.STATUS_VAGUE);
        entry.setReviewStage(LearningConstants.Review.INITIAL_STAGE);
        entry.setMasteryScore(LearningConstants.Review.INITIAL_MASTERY);
        entry.setNextReviewTime(now);
        entry.setDueCount(LearningConstants.Review.INITIAL_STAGE);
        entry.setReviewCount(LearningConstants.Review.INITIAL_STAGE);
        entry.setCorrectCount(LearningConstants.Review.INITIAL_STAGE);
        entry.setWrongCount(LearningConstants.Review.INITIAL_STAGE);
        entry.setDeleted(false);
        entry.setCreateTime(now);
        entry.setUpdateTime(now);
        return entry;
    }

    public LearningWordbookEntry copyTo(Long targetWordbookId, LocalDateTime now) {
        LearningWordbookEntry clone = new LearningWordbookEntry();
        clone.setUserId(userId);
        clone.setWordbookId(targetWordbookId);
        clone.setVocabularyId(vocabularyId);
        clone.setTerm(term);
        clone.setNormalizedTerm(normalizedTerm);
        clone.setNote(note);
        clone.setSnapshotRawContent(snapshotRawContent);
        clone.setSnapshotParsedJson(snapshotParsedJson);
        clone.setSnapshotTagsJson(snapshotTagsJson);
        clone.setSnapshotRelationsJson(snapshotRelationsJson);
        clone.setSnapshotProvider(snapshotProvider);
        clone.setSnapshotModelName(snapshotModelName);
        clone.setSnapshotSessionId(snapshotSessionId);
        clone.setSnapshotTime(snapshotTime);
        clone.setStatus(status);
        clone.setReviewStage(reviewStage);
        clone.setMasteryScore(masteryScore);
        clone.setFirstReviewTime(firstReviewTime);
        clone.setLastReviewTime(lastReviewTime);
        clone.setNextReviewTime(nextReviewTime);
        clone.setDueCount(dueCount);
        clone.setReviewCount(reviewCount);
        clone.setCorrectCount(correctCount);
        clone.setWrongCount(wrongCount);
        clone.setDeleted(false);
        clone.setCreateTime(now);
        clone.setUpdateTime(now);
        return clone;
    }

    public void restore(String note, LocalDateTime now) {
        setDeleted(false);
        setNote(note);
        touch(now);
    }

    public void markDeleted(LocalDateTime now) {
        setDeleted(true);
        touch(now);
    }

    public void moveTo(Long targetWordbookId, LocalDateTime now) {
        setWordbookId(targetWordbookId);
        touch(now);
    }

    public void applyVocabularySnapshot(EnglishVocabularyStudyRecord vocabulary, LocalDateTime now,
                                        String tagsJson, String relationsJson) {
        setSnapshotRawContent(vocabulary.getRawContent());
        setSnapshotParsedJson(vocabulary.getParsedJson());
        setSnapshotTagsJson(tagsJson);
        setSnapshotRelationsJson(relationsJson);
        setSnapshotProvider(vocabulary.getProvider());
        setSnapshotModelName(vocabulary.getModelName());
        setSnapshotSessionId(vocabulary.getSessionId());
        setSnapshotTime(now);
    }

    public void refreshVocabularyIdentity(EnglishVocabularyStudyRecord vocabulary, LocalDateTime now) {
        setTerm(vocabulary.getTerm());
        setVocabularyId(vocabulary.getId());
        touch(now);
    }

    public void markDue(LocalDateTime now) {
        setDueCount(dueCount() + LearningConstants.SEQUENCE_STEP);
        touch(now);
    }

    public void recordCorrectReview(int stageAfter, int masteryAfter, String nextStatus) {
        setCorrectCount(correctCount() + LearningConstants.SEQUENCE_STEP);
        recordReviewProgress(stageAfter, masteryAfter, nextStatus);
    }

    public void recordNeutralReview(int stageAfter, int masteryAfter, String nextStatus) {
        recordReviewProgress(stageAfter, masteryAfter, nextStatus);
    }

    public void recordWrongReview(int stageAfter, int masteryAfter, String nextStatus) {
        setWrongCount(wrongCount() + LearningConstants.SEQUENCE_STEP);
        recordReviewProgress(stageAfter, masteryAfter, nextStatus);
    }

    public void completeReview(LocalDateTime reviewTime, LocalDateTime nextTime,
                               int stageAfter, int masteryAfter, LocalDateTime now) {
        if (getFirstReviewTime() == null) {
            setFirstReviewTime(reviewTime);
        }
        setLastReviewTime(reviewTime);
        setNextReviewTime(nextTime);
        setReviewStage(stageAfter);
        setMasteryScore(masteryAfter);
        setReviewCount(reviewCount() + LearningConstants.SEQUENCE_STEP);
        touch(now);
    }

    public int reviewStage() {
        return getReviewStage() == null ? LearningConstants.ZERO : getReviewStage();
    }

    public int masteryScore() {
        return getMasteryScore() == null ? LearningConstants.ZERO : getMasteryScore();
    }

    private void recordReviewProgress(int stageAfter, int masteryAfter, String nextStatus) {
        setReviewStage(stageAfter);
        setMasteryScore(masteryAfter);
        setStatus(nextStatus);
    }

    private int dueCount() {
        return getDueCount() == null ? LearningConstants.ZERO : getDueCount();
    }

    private int reviewCount() {
        return getReviewCount() == null ? LearningConstants.ZERO : getReviewCount();
    }

    private int correctCount() {
        return getCorrectCount() == null ? LearningConstants.ZERO : getCorrectCount();
    }

    private int wrongCount() {
        return getWrongCount() == null ? LearningConstants.ZERO : getWrongCount();
    }

    private void touch(LocalDateTime now) {
        setUpdateTime(now);
    }
}
