package com.chandler.learning.agent.vocabulary.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chandler.learning.agent.vocabulary.domain.bo.WordbookEntrySummaryItem;
import com.chandler.learning.agent.vocabulary.domain.entity.LearningWordbookEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 个人单词本数据访问接口。
 */
@Mapper
public interface LearningWordbookEntryMapper extends BaseMapper<LearningWordbookEntry> {

    /**
     * 唯一键包含逻辑删除行，重新导入时需要显式读取并恢复旧词条。
     */
    LearningWordbookEntry selectIncludingDeleted(@Param("wordbookId") Long wordbookId,
                                                  @Param("normalizedTerm") String normalizedTerm);

    /**
     * 发布整本词表前一次读取目标单词本，避免逐词查询。
     */
    List<LearningWordbookEntry> selectAllIncludingDeleted(@Param("wordbookId") Long wordbookId);

    /** 按归一化词批量读取单词本词条，包含已逻辑删除的数据。 */
    List<LearningWordbookEntry> selectByNormalizedTermsIncludingDeleted(
            @Param("wordbookId") Long wordbookId,
            @Param("normalizedTerms") List<String> normalizedTerms);

    /**
     * MyBatis-Plus 的逻辑删除条件会阻止 updateById 恢复旧行，先显式撤销删除标记。
     */
    int restoreDeletedById(@Param("entryId") Long entryId);

    /** 批量新增从公共词表导入的个人单词本词条。 */
    int insertBatch(@Param("list") List<LearningWordbookEntry> list);

    /** 批量刷新已存在的个人单词本词条及其快照信息。 */
    int updateImportedBatch(@Param("list") List<LearningWordbookEntry> list);

    /** 批量创建或恢复场景学习使用的个人词条。 */
    int upsertLearningBatch(@Param("list") List<LearningWordbookEntry> list);

    /** 批量冻结个人词条的词卡快照。 */
    int updateVocabularyCardBatch(@Param("list") List<LearningWordbookEntry> list);

    /**
     * 分页读取单词本列表投影。完整词卡 JSON 不随列表返回，避免大字段传输和逐条解析。
     */
    Page<WordbookEntrySummaryItem> selectSummaryPage(
            Page<WordbookEntrySummaryItem> page,
            @Param("userId") Long userId,
            @Param("wordbookId") Long wordbookId,
            @Param("status") String status,
            @Param("keyword") String keyword,
            @Param("dueOnly") boolean dueOnly,
            @Param("now") java.time.LocalDateTime now);

    /** 查询已到期的轻量复习队列，词卡正文由学习页按需读取。 */
    List<WordbookEntrySummaryItem> selectDueSummary(
            @Param("userId") Long userId,
            @Param("wordbookId") Long wordbookId,
            @Param("now") java.time.LocalDateTime now,
            @Param("limit") int limit);

    /** 查询重新生成复习队列的轻量投影，避免逐条回查完整词卡。 */
    List<WordbookEntrySummaryItem> selectRestartSummary(
            @Param("userId") Long userId,
            @Param("wordbookId") Long wordbookId,
            @Param("limit") int limit);

    /** 一次查询词本与词汇缓存中的可下载发音词，避免维护任务分页循环访问数据库。 */
    List<String> selectDistinctAudioTerms();
}
