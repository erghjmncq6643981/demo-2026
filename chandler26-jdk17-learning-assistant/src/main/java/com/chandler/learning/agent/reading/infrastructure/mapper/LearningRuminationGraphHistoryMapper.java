package com.chandler.learning.agent.reading.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.reading.domain.entity.LearningRuminationGraphHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 知识反刍场景星系图谱历史快照数据访问接口。
 */
@Mapper
public interface LearningRuminationGraphHistoryMapper extends BaseMapper<LearningRuminationGraphHistory> {

    /**
     * 统计指定场景与版本号下有效历史快照记录数。
     *
     * @param userId       所属用户 ID
     * @param wordbookId   所属单词本 ID
     * @param scenarioCode 主题场景编码
     * @param graphVersion 图谱版本号
     * @return 匹配记录数量
     */
    long countByScenarioAndVersion(@Param("userId") Long userId,
                                   @Param("wordbookId") Long wordbookId,
                                   @Param("scenarioCode") String scenarioCode,
                                   @Param("graphVersion") Integer graphVersion);

    /**
     * 查询指定场景最近的紧凑历史版本摘要列表（不加载大体积节点与连线 JSON）。
     *
     * @param userId       所属用户 ID
     * @param wordbookId   所属单词本 ID
     * @param scenarioCode 主题场景编码
     * @param limit        返回条数上限（默认 30）
     * @return 按版本号与主键倒序排列的历史版本摘要列表
     */
    List<LearningRuminationGraphHistory> selectRecentSummaries(@Param("userId") Long userId,
                                                               @Param("wordbookId") Long wordbookId,
                                                               @Param("scenarioCode") String scenarioCode,
                                                               @Param("limit") int limit);

    /**
     * 查询指定场景超出保留上限（如最近 30 次）的较早历史记录主键列表。
     *
     * @param userId       所属用户 ID
     * @param wordbookId   所属单词本 ID
     * @param scenarioCode 主题场景编码
     * @param keepCount    保留的最新记录条数（默认 30）
     * @return 需要淘汰软删除的历史记录 ID 列表
     */
    List<Long> selectOverflowHistoryIds(@Param("userId") Long userId,
                                        @Param("wordbookId") Long wordbookId,
                                        @Param("scenarioCode") String scenarioCode,
                                        @Param("keepCount") int keepCount);

    /**
     * 批量逻辑删除超限的历史快照记录。
     *
     * @param ids      待逻辑删除的历史主键集合
     * @param updateBy 操作用户 ID
     * @return 影响行数
     */
    int softDeleteByIds(@Param("ids") List<Long> ids,
                        @Param("updateBy") Long updateBy);
}
