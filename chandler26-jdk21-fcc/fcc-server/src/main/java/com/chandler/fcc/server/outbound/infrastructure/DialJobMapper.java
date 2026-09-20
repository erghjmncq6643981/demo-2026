package com.chandler.fcc.server.outbound.infrastructure;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 自动外呼任务及逐次尝试的持久化端口。
 */
@Mapper
public interface DialJobMapper {

    /**
     * 创建具备业务幂等键的外呼任务。
     *
     * @param row 任务参数
     * @return 写入数量
     */
    int create(Map<String, Object> row);

    /**
     * 分页查询指定坐席的外呼任务。
     *
     * @param owner 坐席工号
     * @param offset 分页偏移
     * @return 任务摘要
     */
    List<Map<String, Object>> list(@Param("owner") String owner, @Param("offset") int offset);

    /**
     * 查询指定坐席有权访问的任务详情。
     *
     * @param owner 坐席工号
     * @param id 任务标识
     * @return 任务详情，不存在时为空
     */
    Map<String, Object> detail(@Param("owner") String owner, @Param("id") String id);

    /**
     * 查询任务的逐次尝试结果。
     *
     * @param id 任务标识
     * @return 尝试记录
     */
    List<Map<String, Object>> attempts(@Param("id") String id);

    /**
     * 领取下一条满足执行条件的待运行任务。
     *
     * @return 已锁定任务，不存在时为空
     */
    Map<String, Object> next();

    /**
     * 将任务从待执行状态原子变更为运行中。
     *
     * @param id 任务标识
     * @return 更新数量
     */
    int claim(@Param("id") String id);

    /**
     * 统计任务已创建的尝试次数。
     *
     * @param id 任务标识
     * @return 尝试次数
     */
    int countAttempts(@Param("id") String id);

    /**
     * 创建运行中的外呼尝试。
     *
     * @param row 尝试参数
     * @return 写入数量
     */
    int startAttempt(Map<String, Object> row);

    /**
     * 将外呼尝试关联到已持久化的通话事实。
     *
     * @param attempt 尝试标识
     * @param call 通话标识
     * @return 更新数量
     */
    int attach(@Param("attempt") String attempt, @Param("call") String call);

    /**
     * 查询需要根据通话事实对账的运行中尝试。
     *
     * @return 至多一百条尝试
     */
    List<Map<String, Object>> running();

    /**
     * 根据已结束通话原子回填一批尝试及所属任务。
     *
     * @param ids 非空尝试标识集合
     * @return 更新行数
     */
    int reconcileBatch(@Param("ids") List<String> ids);

    /**
     * 锁定仍处于派发租期的尝试。
     *
     * @param id 尝试标识
     * @return 有效尝试标识，失效时为空
     */
    String lockDispatch(@Param("id") String id);

    /**
     * 查询无通话事实且派发租期已过的尝试。
     *
     * @return 至多一百条尝试标识
     */
    List<String> expiredDispatches();

    /**
     * 原子结束过期派发及任务，不重拨结果未知的请求。
     *
     * @param ids 非空尝试标识集合
     * @return 更新行数
     */
    int expireDispatches(@Param("ids") List<String> ids);

    /**
     * 按业务尝试标识查询已持久化通话。
     *
     * @param attempt 尝试标识
     * @return 通话事实，不存在时为空
     */
    Map<String, Object> call(@Param("attempt") String attempt);

    /**
     * 完成仍在运行中的尝试。
     *
     * @param id 尝试标识
     * @param status 最终状态
     * @param result 结果代码
     * @return 更新数量
     */
    int finishAttempt(
        @Param("id") String id,
        @Param("status") String status,
        @Param("result") String result
    );

    /**
     * 完成任务或安排下一次重试。
     *
     * @param id 任务标识
     * @param status 目标状态
     * @return 更新数量
     */
    int finishJob(@Param("id") String id, @Param("status") String status);

    /**
     * 由任务归属坐席暂停、恢复或取消任务。
     *
     * @param owner 坐席工号
     * @param id 任务标识
     * @param action 操作代码
     * @return 更新数量
     */
    int control(
        @Param("owner") String owner,
        @Param("id") String id,
        @Param("action") String action
    );

    /**
     * 统计全局运行中尝试数。
     *
     * @return 运行中尝试数
     */
    int activeCount();

    /**
     * 获取全局调度容量锁。
     *
     * @return 固定锁标识
     */
    Long schedulerLock();

    /**
     * 统计号码最近二十四小时内的外呼次数。
     *
     * @param number 规范化号码
     * @return 外呼次数
     */
    int frequency(@Param("number") String number);
}
