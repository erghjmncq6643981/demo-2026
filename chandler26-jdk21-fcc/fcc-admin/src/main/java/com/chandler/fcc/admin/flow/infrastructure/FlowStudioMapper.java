package com.chandler.fcc.admin.flow.infrastructure;

import com.chandler.fcc.admin.flow.infrastructure.data.FlowExecutionInstanceData;
import com.chandler.fcc.admin.flow.infrastructure.data.FlowExecutionStepData;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 流程画布与单通话轨迹查询持久化端口。
 */
@Mapper
public interface FlowStudioMapper {
    /**
     * 查询通话存在性。
     *
     * @param call 通话
     * @return 数量
     */
    int callExists(@Param("call") String call);

    /**
     * 查询固定版本和执行上下文。
     *
     * @param call 通话
     * @return 实例摘要
     */
    FlowExecutionInstanceData instance(@Param("call") String call);

    /**
     * 按游标分页读取阶段尝试。
     *
     * @param call 通话
     * @param after 游标
     * @return 最多 100 条事实
     */
    List<FlowExecutionStepData> steps(
        @Param("call") String call,
        @Param("after") String after
    );
}
