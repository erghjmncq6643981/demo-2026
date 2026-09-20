package com.chandler.fcc.admin.flow;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

/** 流程画布与单通话轨迹查询，全部查询显式限定租户。 */
@Mapper
public interface FlowStudioMapper {
    /** 创建租户流程。 @param row 流程元数据 */
    void create(Map<String,Object> row);
    /** 查询本租户通话存在性。 @param tenant 租户 @param call 通话 @return 数量 */
    int callExists(@Param("tenant") long tenant,@Param("call") String call);
    /** 查询固定版本和执行上下文。 @param tenant 租户 @param call 通话 @return 实例摘要 */
    Map<String,Object> instance(@Param("tenant") long tenant,@Param("call") String call);
    /** 按游标分页读取阶段尝试。 @param tenant 租户 @param call 通话 @param after 游标 @return 最多 100 条事实 */
    List<Map<String,Object>> steps(@Param("tenant") long tenant,@Param("call") String call,@Param("after") String after);
}
