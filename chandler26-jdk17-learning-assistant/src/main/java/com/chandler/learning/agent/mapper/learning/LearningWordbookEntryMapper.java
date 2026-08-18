package com.chandler.learning.agent.mapper.learning;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.domain.entity.learning.LearningWordbookEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * LearningWordbookEntryMapper 类。
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

    /**
     * MyBatis-Plus 的逻辑删除条件会阻止 updateById 恢复旧行，先显式撤销删除标记。
     */
    int restoreDeletedById(@Param("entryId") Long entryId);

    /** 批量新增从公共词表导入的个人单词本词条。 */
    int insertBatch(@Param("list") List<LearningWordbookEntry> list);

    /** 批量刷新已存在的个人单词本词条及其快照信息。 */
    int updateImportedBatch(@Param("list") List<LearningWordbookEntry> list);
}
