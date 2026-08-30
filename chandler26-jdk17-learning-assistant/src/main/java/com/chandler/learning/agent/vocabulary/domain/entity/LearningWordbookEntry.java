package com.chandler.learning.agent.vocabulary.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chandler.learning.agent.common.persistence.BaseEntity;
import com.chandler.learning.agent.vocabulary.domain.entity.EnglishVocabularyStudyRecord;
import com.chandler.learning.agent.common.constant.CommonConstants;
import com.chandler.learning.agent.vocabulary.domain.constant.ReviewConstants;
import com.chandler.learning.agent.vocabulary.domain.constant.VocabularyCardConstants;
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

    /** 用户跨词本共享的逐词进度 ID。 */
    private Long progressId;

    /** 导入词表词条 ID。 */
    private Long catalogEntryId;

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

    /** 词卡状态：missing、queued、generating、ready、failed、not_required。 */
    private String cardStatus;

    /** 词卡生成失败原因。 */
    private String cardErrorMessage;

    /** 词卡生成完成时间。 */
    private LocalDateTime cardGeneratedTime;

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

    /** 创建新的个人单词本词条。 */
    public static LearningWordbookEntry createNew(Long userId, Long wordbookId, EnglishVocabularyStudyRecord vocabulary,
                                                  String note, LocalDateTime now) {
        LearningWordbookEntry entry = new LearningWordbookEntry();
        entry.setUserId(userId);
        entry.setWordbookId(wordbookId);
        entry.setVocabularyId(vocabulary.getId());
        entry.setTerm(vocabulary.getTerm());
        entry.setNormalizedTerm(vocabulary.getNormalizedTerm());
        entry.setNote(note);
        entry.setCardStatus(VocabularyCardConstants.STATUS_READY);
        entry.setCardGeneratedTime(now);
        entry.applyVocabularySnapshot(vocabulary, now, null, null);
        entry.setStatus(ReviewConstants.STATUS_VAGUE);
        entry.setReviewStage(ReviewConstants.INITIAL_STAGE);
        entry.setMasteryScore(ReviewConstants.INITIAL_MASTERY);
        entry.setNextReviewTime(now);
        entry.setDueCount(ReviewConstants.INITIAL_STAGE);
        entry.setReviewCount(ReviewConstants.INITIAL_STAGE);
        entry.setCorrectCount(ReviewConstants.INITIAL_STAGE);
        entry.setWrongCount(ReviewConstants.INITIAL_STAGE);
        entry.setDeleted(false);
        entry.setCreateTime(now);
        entry.setUpdateTime(now);
        return entry;
    }

    /**
     * 创建仅包含导入音标和释义的个人词条，AI 词卡稍后按场景需要生成。
     */
    public static LearningWordbookEntry createImported(Long userId, Long wordbookId, Long progressId,
                                                        Long catalogEntryId, String term, String normalizedTerm,
                                                        String basicSnapshotJson, LocalDateTime now) {
        LearningWordbookEntry entry = new LearningWordbookEntry();
        entry.setId(com.baomidou.mybatisplus.core.toolkit.IdWorker.getId());
        entry.setUserId(userId);
        entry.setWordbookId(wordbookId);
        entry.setProgressId(progressId);
        entry.setCatalogEntryId(catalogEntryId);
        entry.setTerm(term);
        entry.setNormalizedTerm(normalizedTerm);
        entry.setSnapshotParsedJson(basicSnapshotJson);
        entry.setSnapshotTime(now);
        entry.setCardStatus(VocabularyCardConstants.STATUS_NOT_REQUIRED);
        entry.setStatus(ReviewConstants.STATUS_VAGUE);
        entry.setReviewStage(ReviewConstants.INITIAL_STAGE);
        entry.setMasteryScore(ReviewConstants.INITIAL_MASTERY);
        entry.setNextReviewTime(now);
        entry.setDueCount(CommonConstants.ZERO);
        entry.setReviewCount(CommonConstants.ZERO);
        entry.setCorrectCount(CommonConstants.ZERO);
        entry.setWrongCount(CommonConstants.ZERO);
        entry.setCreateBy(userId);
        entry.setUpdateBy(userId);
        entry.setDeleted(false);
        entry.setVersion(CommonConstants.ZERO);
        entry.setCreateTime(now);
        entry.setUpdateTime(now);
        return entry;
    }

    /** 复制词条及个人学习状态到目标单词本。 */
    public LearningWordbookEntry copyTo(Long targetWordbookId, LocalDateTime now) {
        LearningWordbookEntry clone = new LearningWordbookEntry();
        clone.setUserId(userId);
        clone.setWordbookId(targetWordbookId);
        clone.setProgressId(progressId);
        clone.setCatalogEntryId(catalogEntryId);
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
        clone.setCardStatus(cardStatus);
        clone.setCardErrorMessage(cardErrorMessage);
        clone.setCardGeneratedTime(cardGeneratedTime);
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

    /** 恢复已逻辑删除的个人词条。 */
    public void restore(String note, LocalDateTime now) {
        setDeleted(false);
        setNote(note);
        touch(now);
    }

    /** 将个人词条标记为逻辑删除。 */
    public void markDeleted(LocalDateTime now) {
        setDeleted(true);
        touch(now);
    }

    /** 把个人词条移动到目标单词本。 */
    public void moveTo(Long targetWordbookId, LocalDateTime now) {
        setWordbookId(targetWordbookId);
        touch(now);
    }

    /** 应用个人单词本状态变更。 */
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
        setCardStatus(VocabularyCardConstants.STATUS_READY);
        setCardErrorMessage(null);
        setCardGeneratedTime(now);
    }

    /** 刷新个人词条关联的公共词卡身份。 */
    public void refreshVocabularyIdentity(EnglishVocabularyStudyRecord vocabulary, LocalDateTime now) {
        setTerm(vocabulary.getTerm());
        setVocabularyId(vocabulary.getId());
        touch(now);
    }

    /** 把词条标记为待复习状态。 */
    public void markDue(LocalDateTime now) {
        setDueCount(dueCount() + CommonConstants.SEQUENCE_STEP);
        touch(now);
    }

    /** 累计一次正确复习结果。 */
    public void recordCorrectReview(int stageAfter, int masteryAfter, String nextStatus) {
        setCorrectCount(correctCount() + CommonConstants.SEQUENCE_STEP);
        recordReviewProgress(stageAfter, masteryAfter, nextStatus);
    }

    /** 累计一次模糊复习结果。 */
    public void recordNeutralReview(int stageAfter, int masteryAfter, String nextStatus) {
        recordReviewProgress(stageAfter, masteryAfter, nextStatus);
    }

    /** 累计一次错误复习结果。 */
    public void recordWrongReview(int stageAfter, int masteryAfter, String nextStatus) {
        setWrongCount(wrongCount() + CommonConstants.SEQUENCE_STEP);
        recordReviewProgress(stageAfter, masteryAfter, nextStatus);
    }

    /** 应用复习结果并更新下次复习时间。 */
    public void completeReview(LocalDateTime reviewTime, LocalDateTime nextTime,
                               int stageAfter, int masteryAfter, LocalDateTime now) {
        if (getFirstReviewTime() == null) {
            setFirstReviewTime(reviewTime);
        }
        setLastReviewTime(reviewTime);
        setNextReviewTime(nextTime);
        setReviewStage(stageAfter);
        setMasteryScore(masteryAfter);
        setReviewCount(reviewCount() + CommonConstants.SEQUENCE_STEP);
        touch(now);
    }

    /** 返回当前词条复习阶段。 */
    public int reviewStage() {
        return getReviewStage() == null ? CommonConstants.ZERO : getReviewStage();
    }

    /** 返回当前词条掌握分。 */
    public int masteryScore() {
        return getMasteryScore() == null ? CommonConstants.ZERO : getMasteryScore();
    }

    private void recordReviewProgress(int stageAfter, int masteryAfter, String nextStatus) {
        setReviewStage(stageAfter);
        setMasteryScore(masteryAfter);
        setStatus(nextStatus);
    }

    private int dueCount() {
        return getDueCount() == null ? CommonConstants.ZERO : getDueCount();
    }

    private int reviewCount() {
        return getReviewCount() == null ? CommonConstants.ZERO : getReviewCount();
    }

    private int correctCount() {
        return getCorrectCount() == null ? CommonConstants.ZERO : getCorrectCount();
    }

    private int wrongCount() {
        return getWrongCount() == null ? CommonConstants.ZERO : getWrongCount();
    }

    private void touch(LocalDateTime now) {
        setUpdateTime(now);
    }
}
