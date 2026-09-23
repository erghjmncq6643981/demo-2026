package com.chandler.fcc.server.flow.infrastructure;

import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 查询运行端需要加载的已发布流程定义。
 */
@Mapper
public interface FlowConfigMapper {

    /**
     * 查询指定流程的最新发布定义。
     *
     * @param flowKey 流程唯一键
     * @return 发布定义摘要；不存在时为空
     */
    Map<String, Object> findPublishedByKey(@Param("flowKey") String flowKey);

    /**
     * 查询指定模型类型当前生效的流程定义。
     *
     * @param modelType 流程模型类型
     * @return 发布定义摘要；不存在时为空
     */
    Map<String, Object> findPublishedByModelType(@Param("modelType") String modelType);

    /**
     * 按版本快照 ID 查询流程定义，供已开始的通话恢复固定版本。
     *
     * @param versionId 流程版本 ID
     * @return 流程定义摘要；不存在时为空
     */
    Map<String, Object> findByVersionId(@Param("versionId") String versionId);
}
