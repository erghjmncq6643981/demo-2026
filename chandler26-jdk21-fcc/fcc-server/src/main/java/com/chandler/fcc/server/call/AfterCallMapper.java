package com.chandler.fcc.server.call;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.Map;

/** 话后结果与本人已结束通话的原子关联。 */
@Mapper
public interface AfterCallMapper {
    /** 锁定本人已结束的通话。
     * @param tenant 租户 @param owner 坐席 @param call 通话 @return 通话标识或空
     */
    String lockEnded(@Param("tenant") long tenant,@Param("owner") String owner,@Param("call") String call);
    /** 保存一次性话后结果，重试不覆盖首次提交。
     * @param tenant 租户 @param owner 坐席 @param call 通话 @param payload 小结 JSON @return 写入数
     */
    int save(@Param("tenant") long tenant,@Param("owner") String owner,@Param("call") String call,@Param("payload") String payload);
    /** 查询本人小结详情。
     * @param tenant 租户 @param owner 坐席 @param call 通话 @return 小结 JSON 或空
     */
    String detail(@Param("tenant") long tenant,@Param("owner") String owner,@Param("call") String call);
}
