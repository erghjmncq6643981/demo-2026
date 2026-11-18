package com.chandler.learning.agent.vocabulary.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.vocabulary.domain.entity.LearningVocabularyLookupHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户个人查词与搜索历史足迹 Mapper。
 */
@Mapper
public interface LearningVocabularyLookupHistoryMapper extends BaseMapper<LearningVocabularyLookupHistory> {

    /**
     * 原子写入或累加用户对单个词汇的查词记录。
     */
    int upsertLookup(@Param("item") LearningVocabularyLookupHistory item);

    /**
     * 查询指定起始时间以来用户搜索过的词汇（按最近搜索时间倒序）。
     */
    List<LearningVocabularyLookupHistory> selectSinceTime(
            @Param("userId") Long userId,
            @Param("sinceTime") LocalDateTime sinceTime,
            @Param("limit") int limit);

    /**
     * 查询用户最近搜索过的 N 个词汇（按最近搜索时间倒序）。
     */
    List<LearningVocabularyLookupHistory> selectRecent(
            @Param("userId") Long userId,
            @Param("limit") int limit);

    /**
     * 记录用户在近期查词复习流中的最近过关时间。
     */
    int markReviewed(
            @Param("userId") Long userId,
            @Param("normalizedTerm") String normalizedTerm,
            @Param("reviewedTime") LocalDateTime reviewedTime);
}
