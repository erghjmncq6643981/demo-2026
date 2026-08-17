package com.chandler.learning.agent.mapper.learning;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.domain.entity.learning.LearningWordbookEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * LearningWordbookEntryMapper 类。
 */
@Mapper
public interface LearningWordbookEntryMapper extends BaseMapper<LearningWordbookEntry> {

    /**
     * 唯一键包含逻辑删除行，重新导入时需要显式读取并恢复旧词条。
     */
    @Select("SELECT * FROM learning_wordbook_entry WHERE wordbook_id = #{wordbookId} "
            + "AND normalized_term = #{normalizedTerm} LIMIT 1")
    LearningWordbookEntry selectIncludingDeleted(@Param("wordbookId") Long wordbookId,
                                                  @Param("normalizedTerm") String normalizedTerm);

    /**
     * 发布整本词表前一次读取目标单词本，避免逐词查询。
     */
    @Select("SELECT * FROM learning_wordbook_entry WHERE wordbook_id = #{wordbookId}")
    List<LearningWordbookEntry> selectAllIncludingDeleted(@Param("wordbookId") Long wordbookId);

    /**
     * MyBatis-Plus 的逻辑删除条件会阻止 updateById 恢复旧行，先显式撤销删除标记。
     */
    @Update("UPDATE learning_wordbook_entry SET deleted = 0 WHERE id = #{entryId} AND deleted = 1")
    int restoreDeletedById(@Param("entryId") Long entryId);
}
