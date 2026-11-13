package com.chandler.learning.agent.reading.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.reading.domain.entity.LearningRuminationSentencePractice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 知识反刍按句跟敲与目标词遮挡补全巩固记录数据访问接口。
 */
@Mapper
public interface LearningRuminationSentencePracticeMapper extends BaseMapper<LearningRuminationSentencePractice> {

    /** 加行级排他锁读取指定句子与目标词的巩固记录，保证并发提交顺序更新。 */
    LearningRuminationSentencePractice selectByKeyForUpdate(
            @Param("userId") Long userId,
            @Param("materialId") Long materialId,
            @Param("sentenceIndex") Integer sentenceIndex,
            @Param("targetWord") String targetWord);

    /** 批量查询多个场景材料关联的句子巩固记录，避免列表装配发生循环查库。 */
    List<LearningRuminationSentencePractice> selectByMaterialIds(
            @Param("userId") Long userId,
            @Param("materialIds") List<Long> materialIds);

    /** 统计同一材料中指定目标词已触发过掌握度结算的记录数。 */
    int countMasterySettledForTargetWord(
            @Param("userId") Long userId,
            @Param("materialId") Long materialId,
            @Param("targetWord") String targetWord);

    /**
     * 物理删除指定场景材料下的句型跟敲练习记录（用于覆盖重新生成时避开唯一索引冲突）。
     */
    int deleteByMaterialId(
            @Param("userId") Long userId,
            @Param("materialId") Long materialId);
}
