package com.chandler.fcc.server.agent.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.Map;
import java.util.List;

/** 坐席占用、外呼资源及会话恢复查询。 */
@Mapper
public interface AgentRuntimeMapper {
 /** 读取启用坐席。 @param tenant 租户 @param owner 坐席 @return 坐席摘要 */
 Map<String,Object> agent(@Param("tenant") long tenant,@Param("owner") String owner);
 /** 创建状态记录。 @param tenant 租户 @param owner 坐席 @return 写入数 */
 int ensurePresence(@Param("tenant") long tenant,@Param("owner") String owner);
 /** 更新非通话状态。 @param tenant 租户 @param owner 坐席 @param state 就绪/休息 @return 修改数 */
 int setPresence(@Param("tenant") long tenant,@Param("owner") String owner,@Param("state") String state);
 /** 原子占用坐席。 @param tenant 租户 @param owner 坐席 @param callId 通话 @return 修改数 */
 int reserve(@Param("tenant") long tenant,@Param("owner") String owner,@Param("callId") String callId);
 /** 幂等释放至整理态。 @param tenant 租户 @param owner 坐席 @param callId 通话 @return 修改数 */
 int release(@Param("tenant") long tenant,@Param("owner") String owner,@Param("callId") String callId);
 /** 读取当前状态。 @param tenant 租户 @param owner 坐席 @return 状态 */
 Map<String,Object> presence(@Param("tenant") long tenant,@Param("owner") String owner);
 /** 读取租户启用中继和主叫配置。 @param tenant 租户 @return 首个可用出局资源 */
 Map<String,Object> outbound(@Param("tenant") long tenant);
 /** 检查内部目标分机。 @param tenant 租户 @param number 号码 @return 数量 */
 int internal(@Param("tenant") long tenant,@Param("number") String number);
 /** 分页恢复活跃通话。 @param after 上一页末尾标识 @return 持久会话 */
 List<Map<String,Object>> active(@Param("after") long after);
 /** 按 DID 定位唯一租户和发布流程。 @param number DID @return 路由定义，最多两条用于歧义检查 */
 List<Map<String,Object>> inbound(@Param("number")String number);
 /** 选择技能组最久空闲坐席。 @param tenant 租户 @param group 组代码 @param excluded 已尝试坐席 @return 候选 */
 Map<String,Object> candidate(@Param("tenant")long tenant,@Param("group")String group,@Param("excluded")List<String> excluded);
 /** 追加漏话回拨。 @param id 回拨标识 @param tenant 租户 @param callId 通话 @param number 号码 @param did 热线 @param reason 原因 @return 写入数 */
 int callback(@Param("id")long id,@Param("tenant")long tenant,@Param("callId")String callId,@Param("number")String number,@Param("did")String did,@Param("reason")String reason);
}
