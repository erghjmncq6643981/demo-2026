package com.chandler.fcc.server.event.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import com.chandler.fcc.server.event.controller.resp.EventRecoveryIssueResp;

/**
 * 持久事件收件箱端口，业务副作用发生前先保存原始事实。
 */
@Mapper
public interface EventInboxMapper {

    /** @param offset 分页起点 @return 未知事件脱敏摘要，最多五十条 */
    List<EventRecoveryIssueResp> recoveryIssues(@Param("offset") int offset);

    /** @param id 事件标识 @return 已验证过来源的原始信封 */
    String recoveryPayload(@Param("id") String id);

    /** @param id 事件标识 @param token 唯一租约令牌 @param actor 操作者工号 @return 原子领取数量，最多十次，过期租约可重新领取 */
    int claimRecovery(@Param("id") String id, @Param("token") String token, @Param("actor") String actor);

    /** @param id 事件标识 @param token 当前租约令牌 @param status 修复结果 @param error 脱敏分类 @return 条件更新数量，过期执行者不得覆盖新结果 */
    int finishRecovery(@Param("id") String id, @Param("token") String token, @Param("status") String status, @Param("error") String error);

    /** @param id 事件标识 @param token 本次动作标识 @param actor 操作者 @return 持久审计写入数量；成功后才允许修复事实 */
    int recordRecoveryAudit(@Param("id") String id, @Param("token") String token, @Param("actor") String actor);

    /** @param token 本次动作标识 @param status 动作结果 @param error 脱敏分类 @return 审计结果更新数量 */
    int finishRecoveryAudit(@Param("token") String token, @Param("status") String status, @Param("error") String error);

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
