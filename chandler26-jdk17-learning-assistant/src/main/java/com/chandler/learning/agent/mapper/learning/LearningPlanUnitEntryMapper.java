package com.chandler.learning.agent.mapper.learning;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.domain.entity.learning.LearningPlanUnitEntry;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface LearningPlanUnitEntryMapper extends BaseMapper<LearningPlanUnitEntry> {

    @Insert("<script>" +
            "INSERT INTO learning_plan_unit_entry (" +
            "id, plan_id, unit_id, catalog_entry_id, wordbook_entry_id, word_progress_id, " +
            "source_order, term, normalized_term, phonetic, meaning_text, context_meaning, " +
            "tier, mastery_requirement, accepted_spellings_json, assessment_json, " +
            "first_learning, sort_order, create_by, create_time, update_by, update_time, deleted, version" +
            ") VALUES " +
            "<foreach collection='list' item='item' separator=','>" +
            "(" +
            "#{item.id}, #{item.planId}, #{item.unitId}, #{item.catalogEntryId}, #{item.wordbookEntryId}, #{item.wordProgressId}, " +
            "#{item.sourceOrder}, #{item.term}, #{item.normalizedTerm}, #{item.phonetic}, #{item.meaningText}, #{item.contextMeaning}, " +
            "#{item.tier}, #{item.masteryRequirement}, #{item.acceptedSpellingsJson}, #{item.assessmentJson}, " +
            "#{item.firstLearning}, #{item.sortOrder}, #{item.createBy}, #{item.createTime}, #{item.updateBy}, #{item.updateTime}, #{item.deleted}, #{item.version}" +
            ")" +
            "</foreach>" +
            "</script>")
    int insertBatch(@Param("list") List<LearningPlanUnitEntry> list);
}
