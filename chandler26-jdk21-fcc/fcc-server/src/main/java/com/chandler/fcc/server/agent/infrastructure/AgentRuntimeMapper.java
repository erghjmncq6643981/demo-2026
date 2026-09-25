package com.chandler.fcc.server.agent.infrastructure;

import com.chandler.fcc.server.agent.infrastructure.data.AgentRuntimeStateData;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 坐席占用、外呼资源及会话恢复持久化端口。
 */
@Mapper
public interface AgentRuntimeMapper {

    /**
     * 完成本人最新已结束通话的整理态，不覆盖后续占用。
     *
     * @param owner 坐席工号
     * @param callId 通话标识
     * @return 更新数量
     */
    int completeAcw(@Param("owner") String owner, @Param("callId") String callId);

    /**
     * 查询话后整理超过时限的坐席。
     *
     * @param timeoutSeconds 超时秒数
     * @return 待置闲的坐席列表
     */
    List<Map<String, Object>> findExpiredAcwAgents(@Param("timeoutSeconds") int timeoutSeconds);

    /**
     * 将超过整理时限的坐席强制按登录态恢复为空闲或示忙。
     *
     * @param timeoutSeconds 超时秒数
     * @return 更新数量
     */
    int expireAcw(@Param("timeoutSeconds") int timeoutSeconds);

    /**
     * 读取启用坐席。
     *
     * @param owner 坐席工号
     * @return 坐席摘要，不存在时为空
     */
    Map<String, Object> agent(@Param("owner") String owner);

    /**
     * 按已认证终端查找唯一启用坐席，不依赖易失的注册在线投影。
     *
     * @param extension 已认证的 SIP/WebRTC 终端账号
     * @return 坐席与终端摘要，不存在时为空
     */
    Map<String, Object> agentByEndpoint(@Param("extension") String extension);

    /**
     * 初始化坐席状态记录。
     *
     * @param owner 坐席工号
     * @return 插入数量
     */
    int ensurePresence(@Param("owner") String owner);

    /**
     * 更新非通话状态。
     *
     * @param owner 坐席工号
     * @param state 就绪或休息状态
     * @return 更新数量
     */
    int setLoginStatus(@Param("owner") String owner, @Param("status") String status);

    /**
     * 原子占用坐席。
     *
     * @param owner 坐席工号
     * @param callId 通话标识
     * @return 更新数量
     */
    int reserveInbound(@Param("owner") String owner, @Param("callId") String callId);

    /**
     * 原子占用外呼坐席；LOGIN_BUSY 仍允许主动外呼。
     *
     * @param owner 坐席工号
     * @param callId 通话标识
     * @return 更新数量
     */
    int reserveOutbound(@Param("owner") String owner, @Param("callId") String callId);

    /**
     * 原子接管由坐席终端主动建立的话道。
     *
     * @param owner 坐席工号
     * @param callId 通话标识
     * @return 更新数量
     */
    int reserveOriginated(@Param("owner") String owner, @Param("callId") String callId);

    /**
     * 根据真实话道事件推进坐席通话工作状态。
     *
     * @param owner 坐席工号
     * @param callId 通话标识
     * @param status CALLING、RINGING 或 ANSWERED
     * @return 更新数量
     */
    int updateCallStatus(
        @Param("owner") String owner,
        @Param("callId") String callId,
        @Param("status") String status
    );

    /**
     * 幂等释放坐席至整理态。
     *
     * @param owner 坐席工号
     * @param callId 通话标识
     * @return 更新数量
     */
    int release(@Param("owner") String owner, @Param("callId") String callId);

    /**
     * 读取当前坐席状态。
     *
     * @param owner 坐席工号
     * @return 当前状态
     */
    AgentRuntimeStateData presence(@Param("owner") String owner);

    /**
     * 读取首个可用出局资源。
     *
     * @return 主叫号码和拨号上下文配置
     */
    Map<String, Object> outbound();

    /**
     * 判断目标号码是否为内部启用分机。
     *
     * @param number 目标号码
     * @return 匹配数量
     */
    int internal(@Param("number") String number);

    /**
     * 分页恢复活跃通话。
     *
     * @param after 上一页末尾标识
     * @return 活跃会话摘要
     */
    List<Map<String, Object>> active(@Param("after") long after);

    /**
     * 按 DID 定位已发布流程版本。
     *
     * @param number DID 号码
     * @param context FreeSWITCH 入局拨号计划上下文
     * @return 路由键和版本标识，最多两条用于歧义检查
     */
    List<Map<String, Object>> inbound(
        @Param("number") String number,
        @Param("context") String context
    );

    /**
     * 选择技能组内最久空闲坐席。
     *
     * @param group 技能组代码
     * @param excluded 已尝试坐席工号
     * @return 候选坐席
     */
    Map<String, Object> candidate(
        @Param("group") String group,
        @Param("excluded") List<String> excluded
    );

    /**
     * 追加漏话回拨任务。
     *
     * @param id 回拨标识
     * @param callId 来源通话标识
     * @param number 客户号码
     * @param did 热线号码
     * @param reason 漏话原因
     * @return 插入数量
     */
    int callback(
        @Param("id") long id,
        @Param("callId") String callId,
        @Param("number") String number,
        @Param("did") String did,
        @Param("reason") String reason
    );
}
