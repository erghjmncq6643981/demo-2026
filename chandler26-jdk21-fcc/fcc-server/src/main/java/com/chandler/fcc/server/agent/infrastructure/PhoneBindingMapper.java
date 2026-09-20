package com.chandler.fcc.server.agent.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.Map;

/** 话机绑定事务持久化端口，租户锁串行化少量绑定操作。 */
@Mapper
public interface PhoneBindingMapper {
    /** 检查当前租户的分机是否启用。
     * @param tenant 租户 @param extension 分机 @return 可用分机数量
     */
    int extensionExists(@Param("tenant") long tenant,@Param("extension") String extension);
    /** 保存短时挑战，替换同一坐席的旧挑战。
     * @param tenant 租户 @param owner 坐席 @param extension 分机 @param hash 一次码摘要 @return 写入数
     */
    int challenge(@Param("tenant") long tenant,@Param("owner") String owner,@Param("extension") String extension,@Param("hash") String hash);
    /** 锁定有效挑战。
     * @param hash 摘要 @param extension 经过 SIP 认证的分机 @return 挑战身份
     */
    Map<String,Object> lockChallenge(@Param("hash") String hash,@Param("extension") String extension);
    /** 创建租户锁行。
     * @param tenant 租户 @return 写入数
     */
    int ensureLock(@Param("tenant") long tenant);
    /** 取得租户绑定互斥锁。
     * @param tenant 租户 @return 租户标识
     */
    Long lockTenant(@Param("tenant") long tenant);
    /** 检查当前坐席与被替换坐席是否在通话中。
     * @param tenant 租户 @param owner 坐席 @param extension 分机 @return 占用数量
     */
    int busy(@Param("tenant") long tenant,@Param("owner") String owner,@Param("extension") String extension);
    /** 失效双方旧终端记录。
     * @param tenant 租户 @param owner 坐席 @param extension 分机 @return 变更数量
     */
    int disableBindings(@Param("tenant") long tenant,@Param("owner") String owner,@Param("extension") String extension);
    /** 清理双方旧分机映射。
     * @param tenant 租户 @param owner 坐席 @param extension 分机 @return 变更数量
     */
    int clearExtensions(@Param("tenant") long tenant,@Param("owner") String owner,@Param("extension") String extension);
    /** 清理旧坐席映射。
     * @param tenant 租户 @param owner 坐席 @param extension 分机 @return 变更数量
     */
    int clearAgents(@Param("tenant") long tenant,@Param("owner") String owner,@Param("extension") String extension);
    /** 绑定分机到启用坐席。
     * @param tenant 租户 @param owner 坐席 @param extension 分机 @return 变更数量
     */
    int bindExtension(@Param("tenant") long tenant,@Param("owner") String owner,@Param("extension") String extension);
    /** 绑定坐席当前分机。
     * @param tenant 租户 @param owner 坐席 @param extension 分机 @return 变更数量
     */
    int bindAgent(@Param("tenant") long tenant,@Param("owner") String owner,@Param("extension") String extension);
    /** 追加可审计的终端绑定记录。
     * @param id 绑定标识 @param tenant 租户 @param owner 坐席 @param extension 分机 @return 写入数量
     */
    int appendBinding(@Param("id") long id,@Param("tenant") long tenant,@Param("owner") String owner,@Param("extension") String extension);
    /** 消耗挑战并记录证明话道。
     * @param hash 摘要 @param channel 认证话道 @return 变更数量
     */
    int consume(@Param("hash") String hash,@Param("channel") String channel);
    /** 查询本人最新绑定挑战结果。
     * @param tenant 租户 @param owner 坐席 @return 状态记录
     */
    Map<String,Object> status(@Param("tenant") long tenant,@Param("owner") String owner);
}
