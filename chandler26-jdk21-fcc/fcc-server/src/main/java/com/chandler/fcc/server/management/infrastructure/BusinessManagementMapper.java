package com.chandler.fcc.server.management.infrastructure;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 管理端客户和自动外呼摘要的持久化端口。
 */
@Mapper
public interface BusinessManagementMapper {

    /**
     * 查询客户摘要。
     *
     * @param flowKey 流程编码筛选，可为空
     * @param phone 号码筛选，可为空
     * @param offset 分页偏移
     * @return 客户摘要
     */
    List<Map<String, Object>> customers(
        @Param("owner") String owner,
        @Param("phone") String phone,
        @Param("offset") int offset
    );

    /**
     * 查询外呼任务摘要。
     *
     * @param number 被叫号码筛选，可为空
     * @param triggerSource 触发来源筛选，可为空
     * @param status 任务状态筛选，可为空
     * @param startTime 更新起始时间，可为空
     * @param endTime 更新截止时间，可为空
     * @param limit 每页数量
     * @param offset 分页偏移
     * @return 任务摘要
     */
    List<Map<String, Object>> jobs(
        @Param("number") String number,
        @Param("triggerSource") String triggerSource,
        @Param("status") String status,
        @Param("startTime") java.time.LocalDateTime startTime,
        @Param("endTime") java.time.LocalDateTime endTime,
        @Param("limit") int limit,
        @Param("offset") int offset
    );

    /**
     * 统计满足条件的外呼任务总数。
     */
    long jobsCount(
        @Param("number") String number,
        @Param("triggerSource") String triggerSource,
        @Param("status") String status,
        @Param("startTime") java.time.LocalDateTime startTime,
        @Param("endTime") java.time.LocalDateTime endTime
    );

    /**
     * 查询客户的负责坐席。
     *
     * @param id 客户标识
     * @return 负责工号，不存在时为空
     */
    String customerOwner(@Param("id") String id);

    /**
     * 判断自动外呼任务是否存在。
     *
     * @param id 任务标识
     * @return 匹配数量
     */
    int jobExists(@Param("id") String id);
}
