package com.chandler.fcc.server.call.infrastructure;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 待回拨任务的查询、排他领取与外呼任务关联持久化端口。
 */
@Mapper
public interface CallbackRuntimeMapper {

    /**
     * 分页读取未分配或已分配给当前坐席的回拨摘要。
     *
     * @param owner 当前坐席工号
     * @param number 精确号码筛选，可为空
     * @param offset 分页偏移
     * @return 最多十条回拨摘要
     */
    List<Map<String, Object>> list(
        @Param("owner") String owner,
        @Param("number") String number,
        @Param("offset") int offset
    );

    /**
     * 统计当前坐席可处理的回拨数量。
     *
     * @param owner 当前坐席工号
     * @param number 精确号码筛选，可为空
     * @return 记录总数
     */
    long count(@Param("owner") String owner, @Param("number") String number);

    /**
     * 锁定当前坐席可领取的回拨记录并读取前次调度结果。
     *
     * @param owner 当前坐席工号
     * @param id 回拨标识
     * @return 锁定记录，不存在或已被他人领取时为空
     */
    Map<String, Object> lock(@Param("owner") String owner, @Param("id") String id);

    /**
     * 将回拨记录与同事务创建的外呼任务关联。
     *
     * @param owner 当前坐席工号
     * @param id 回拨标识
     * @param job 外呼任务标识
     * @return 更新数量
     */
    int schedule(
        @Param("owner") String owner,
        @Param("id") String id,
        @Param("job") String job
    );
}
