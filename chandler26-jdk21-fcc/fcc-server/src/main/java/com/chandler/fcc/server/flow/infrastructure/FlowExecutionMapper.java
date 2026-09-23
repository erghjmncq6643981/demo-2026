package com.chandler.fcc.server.flow.infrastructure;

import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 阶段执行事实持久接口；仅在短数据库事务内调用。
 */
@Mapper
public interface FlowExecutionMapper {
    /**
     * 幂等创建通话流程实例。
     *
     * @param row 实例参数
     */
    void create(Map<String, Object> row);

    /**
     * 锁定单通话实例。
     *
     * @param call 通话 ID
     * @return 当前实例
     */
    Map<String, Object> lock(@Param("call") String call);

    /**
     * 结束上一阶段，保留原始尝试。
     *
     * @param row 转移参数
     */
    void finish(Map<String, Object> row);

    /**
     * 追加新阶段尝试。
     *
     * @param row 阶段参数
     */
    void append(Map<String, Object> row);

    /**
     * 推进当前阶段与实例状态。
     *
     * @param row 转移参数
     */
    void advance(Map<String, Object> row);
}
