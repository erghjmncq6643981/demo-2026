package com.chandler.learning.agent.mapper.vocabulary;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.domain.entity.vocabulary.VocabularyCatalogEntry;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface VocabularyCatalogEntryMapper extends BaseMapper<VocabularyCatalogEntry> {

    @Insert("<script>" +
            "INSERT INTO vocabulary_catalog_entry (id, catalog_id, catalog_version_id, source_order, original_term, " +
            "normalized_term, suggested_term, approved_term, phonetic, definition_text, warning_codes, suspicious, " +
            "review_status, published, create_by, create_time, update_by, update_time, deleted, version) VALUES " +
            "<foreach collection='list' item='item' separator=','>" +
            "(#{item.id}, #{item.catalogId}, #{item.catalogVersionId}, #{item.sourceOrder}, #{item.originalTerm}, " +
            "#{item.normalizedTerm}, #{item.suggestedTerm}, #{item.approvedTerm}, #{item.phonetic}, #{item.definitionText}, " +
            "#{item.warningCodes}, #{item.suspicious}, #{item.reviewStatus}, #{item.published}, " +
            "#{item.createBy}, #{item.createTime}, #{item.updateBy}, #{item.updateTime}, #{item.deleted}, #{item.version})" +
            "</foreach>" +
            "</script>")
    int insertBatch(@Param("list") List<VocabularyCatalogEntry> list);
}
