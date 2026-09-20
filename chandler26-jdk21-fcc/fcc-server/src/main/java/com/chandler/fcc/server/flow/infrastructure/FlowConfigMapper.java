package com.chandler.fcc.server.flow.infrastructure;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 查询运行端需要加载的已发布流程定义。
 */
@Mapper
public interface FlowConfigMapper {

    /**
     * 查询每个流程当前标记为发布的定义。
     *
     * @return 发布定义摘要列表
     */
    List<Map<String, Object>> findAllPublished();

    /**
     * 查询指定流程的最新发布定义。
     *
     * @param flowKey 流程唯一键
     * @return 发布定义摘要；不存在时为空
     */
    Map<String, Object> findPublishedByKey(@Param("flowKey") String flowKey);
}
