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

    /**
     * 主键。
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private Long id;

    /**
     * 词条所属用户 ID。
     */
    @Schema(description = "词条所属用户 ID")
    private Long userId;

    /**
     * 词条所在单词本 ID。
     */
    @Schema(description = "词条所在单词本 ID")
    private Long wordbookId;

    /**
     * 公共英语词汇学习缓存 ID。
     */
    @Schema(description = "公共英语词汇学习缓存 ID")
    private Long vocabularyId;

    /**
     * 展示单词或短语，保留 AI 标准化后的大小写。
     */
    @Schema(description = "展示单词或短语，保留 AI 标准化后的大小写")
    private String term;

    /**
     * 归一化单词或短语，用于去重和查询。
     */
    @Schema(description = "归一化单词或短语，用于去重和查询")
    private String normalizedTerm;

    /**
     * 用户 Markdown 学习笔记。
     */
    @Schema(description = "用户 Markdown 学习笔记")
    private String note;

    /**
     * 加入单词本时冻结的 AI 原始回复快照，避免公共缓存刷新影响个人详情。
     */
    @Schema(description = "加入单词本时冻结的 AI 原始回复快照，避免公共缓存刷新影响个人详情")
    private String snapshotRawContent;

    /**
     * 加入单词本时冻结的结构化学习卡 JSON 快照。
     */
    @Schema(description = "加入单词本时冻结的结构化学习卡 JSON 快照")
    private String snapshotParsedJson;

    /**
     * 加入单词本时冻结的标签 JSON 快照。
     */
    @Schema(description = "加入单词本时冻结的标签 JSON 快照")
    private String snapshotTagsJson;

    /**
     * 加入单词本时冻结的关联词 JSON 快照。
     */
    @Schema(description = "加入单词本时冻结的关联词 JSON 快照")
    private String snapshotRelationsJson;

    /**
     * 快照使用的模型供应商。
     */
    @Schema(description = "快照使用的模型供应商")
    private String snapshotProvider;

    /**
     * 快照使用的模型名称。
     */
    @Schema(description = "快照使用的模型名称")
    private String snapshotModelName;

    /**
     * 快照关联的 AI 会话 ID。
     */
    @Schema(description = "快照关联的 AI 会话 ID")
    private Long snapshotSessionId;

    /**
     * 快照生成时间。
     */
    @Schema(description = "快照生成时间")
    private LocalDateTime snapshotTime;

    /**
     * 熟练状态：familiar-熟悉，forgotten-遗忘，vague-模糊。
     */
    @Schema(description = "熟练状态：familiar-熟悉，forgotten-遗忘，vague-模糊")
    private String status;

    /**
     * 艾宾浩斯复习阶段，决定下一次复习间隔。
     */
    @Schema(description = "艾宾浩斯复习阶段，决定下一次复习间隔")
    private Integer reviewStage;

    /**
     * 掌握度 0-100。
     */
    @Schema(description = "掌握度 0-100")
    private Integer masteryScore;

    /**
     * 首次复习时间。
     */
    @Schema(description = "首次复习时间")
    private LocalDateTime firstReviewTime;

    /**
     * 最近复习时间。
     */
    @Schema(description = "最近复习时间")
    private LocalDateTime lastReviewTime;

    /**
     * 下次复习时间。
     */
    @Schema(description = "下次复习时间")
    private LocalDateTime nextReviewTime;

    /**
     * 进入复习队列次数。
     */
    @Schema(description = "进入复习队列次数")
    private Integer dueCount;

    /**
     * 复习次数。
     */
    @Schema(description = "复习次数")
    private Integer reviewCount;

    /**
     * 记住次数。
     */
    @Schema(description = "记住次数")
    private Integer correctCount;

    /**
     * 忘记次数。
     */
    @Schema(description = "忘记次数")
    private Integer wrongCount;

    /**
     * 创建或保存 {@code createNew} 相关业务。
     */
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

    /**
     * 更新 {@code copyTo} 相关业务。
     */
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

    /**
     * 更新 {@code restore} 相关业务。
     */
    public void restore(String note, LocalDateTime now) {
        setDeleted(false);
        setNote(note);
        touch(now);
    }

    /**
     * 更新 {@code markDeleted} 相关业务。
     */
    public void markDeleted(LocalDateTime now) {
        setDeleted(true);
        touch(now);
    }

    /**
     * 更新 {@code moveTo} 相关业务。
     */
    public void moveTo(Long targetWordbookId, LocalDateTime now) {
        setWordbookId(targetWordbookId);
        touch(now);
    }

    /**
     * 更新 {@code applyVocabularySnapshot} 相关业务。
     */
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

    /**
     * 处理 {@code refreshVocabularyIdentity} 相关业务。
     */
    public void refreshVocabularyIdentity(EnglishVocabularyStudyRecord vocabulary, LocalDateTime now) {
        setTerm(vocabulary.getTerm());
        setVocabularyId(vocabulary.getId());
        touch(now);
    }

    /**
     * 更新 {@code markDue} 相关业务。
     */
    public void markDue(LocalDateTime now) {
        setDueCount(dueCount() + LearningConstants.SEQUENCE_STEP);
        touch(now);
    }

    /**
     * 更新 {@code recordCorrectReview} 相关业务。
     */
    public void recordCorrectReview(int stageAfter, int masteryAfter, String nextStatus) {
        setCorrectCount(correctCount() + LearningConstants.SEQUENCE_STEP);
        recordReviewProgress(stageAfter, masteryAfter, nextStatus);
    }

    /**
     * 更新 {@code recordNeutralReview} 相关业务。
     */
    public void recordNeutralReview(int stageAfter, int masteryAfter, String nextStatus) {
        recordReviewProgress(stageAfter, masteryAfter, nextStatus);
    }

    /**
     * 更新 {@code recordWrongReview} 相关业务。
     */
    public void recordWrongReview(int stageAfter, int masteryAfter, String nextStatus) {
        setWrongCount(wrongCount() + LearningConstants.SEQUENCE_STEP);
        recordReviewProgress(stageAfter, masteryAfter, nextStatus);
    }

    /**
     * 更新 {@code completeReview} 相关业务。
     */
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

    /**
     * 处理 {@code reviewStage} 相关业务。
     */
    public int reviewStage() {
        return getReviewStage() == null ? LearningConstants.ZERO : getReviewStage();
    }

    /**
     * 处理 {@code masteryScore} 相关业务。
     */
    public int masteryScore() {
        return getMasteryScore() == null ? LearningConstants.ZERO : getMasteryScore();
    }

    /**
     * 更新 {@code recordReviewProgress} 相关业务。
     */
    private void recordReviewProgress(int stageAfter, int masteryAfter, String nextStatus) {
        setReviewStage(stageAfter);
        setMasteryScore(masteryAfter);
        setStatus(nextStatus);
    }

    /**
     * 处理 {@code dueCount} 相关业务。
     */
    private int dueCount() {
        return getDueCount() == null ? LearningConstants.ZERO : getDueCount();
    }

    /**
     * 处理 {@code reviewCount} 相关业务。
     */
    private int reviewCount() {
        return getReviewCount() == null ? LearningConstants.ZERO : getReviewCount();
    }

    /**
     * 处理 {@code correctCount} 相关业务。
     */
    private int correctCount() {
        return getCorrectCount() == null ? LearningConstants.ZERO : getCorrectCount();
    }

    /**
     * 处理 {@code wrongCount} 相关业务。
     */
    private int wrongCount() {
        return getWrongCount() == null ? LearningConstants.ZERO : getWrongCount();
    }

    /**
     * 更新 {@code touch} 相关业务。
     */
    private void touch(LocalDateTime now) {
        setUpdateTime(now);
    }
}
