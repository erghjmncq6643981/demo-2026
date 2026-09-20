package com.chandler.fcc.server.event.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 持久事件收件箱端口，业务副作用发生前先保存原始事实。
 */
@Mapper
public interface EventInboxMapper {

    /**
     * 按唯一源事件标识领取首次到达的事件。
     *
     * @param id 源事件标识
     * @param node 节点标识
     * @param payload 原始事件信封
     * @return 新增数量，重复事件返回零
     */
    int receive(
        @Param("id") String id,
        @Param("node") String node,
        @Param("payload") String payload
    );

    /**
     * 查询已经领取事件的处理状态。
     *
     * @param id 源事件标识
     * @return 处理状态
     */
    String status(@Param("id") String id);

    /**
     * 原子领取一个明确失败且尚未耗尽次数的事件。
     *
     * @param id 源事件标识
     * @param maxAttempts 最大处理次数
     * @return 成功领取数量
     */
    int claimRetry(@Param("id") String id, @Param("maxAttempts") int maxAttempts);

    /**
     * 持久化事件处理结果和脱敏错误分类。
     *
     * @param id 源事件标识
     * @param status 处理状态
     * @param error 错误分类，不保存原始载荷或敏感内容
     * @return 更新数量
     */
    int finish(
        @Param("id") String id,
        @Param("status") String status,
        @Param("error") String error
    );
}
