package com.chandler.fcc.server.call.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 话后小结与本人已结束通话的原子持久化端口。
 */
@Mapper
public interface AfterCallMapper {

    /**
     * 锁定当前坐席已结束的通话。
     *
     * @param owner 坐席工号
     * @param call 通话标识
     * @return 通话标识，不存在时为空
     */
    String lockEnded(@Param("owner") String owner, @Param("call") String call);

    /**
     * 保存一次性话后结果，重复提交不覆盖首次结果。
     *
     * @param owner 坐席工号
     * @param call 通话标识
     * @param payload 小结 JSON
     * @return 更新数量
     */
    int save(
        @Param("owner") String owner,
        @Param("call") String call,
        @Param("payload") String payload
    );

    /**
     * 查询当前坐席的话后小结。
     *
     * @param owner 坐席工号
     * @param call 通话标识
     * @return 小结 JSON，不存在时为空
     */
    String detail(@Param("owner") String owner, @Param("call") String call);
}
