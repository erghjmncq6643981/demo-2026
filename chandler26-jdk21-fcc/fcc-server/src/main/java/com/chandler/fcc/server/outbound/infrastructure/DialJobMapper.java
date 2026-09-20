package com.chandler.fcc.server.outbound.infrastructure;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.Map;
import java.util.List;

/** 自动外呼任务与逐次结果持久化。 */
@Mapper
public interface DialJobMapper {
 /** 创建幂等任务。 @param row 任务参数 @return 写入数 */ int create(Map<String,Object> row);
 /** 本人分页查询。 @param tenant 租户 @param owner 坐席 @param offset 偏移 @return 摘要 */ List<Map<String,Object>> list(@Param("tenant")long tenant,@Param("owner")String owner,@Param("offset")int offset);
 /** 本人查询详情。 @param tenant 租户 @param owner 坐席 @param id 标识 @return 任务 */ Map<String,Object> detail(@Param("tenant")long tenant,@Param("owner")String owner,@Param("id")String id);
 /** 查询逐次尝试。 @param id 任务标识 @return 尝试记录 */ List<Map<String,Object>> attempts(@Param("id")String id);
 /** 领取下一任务行锁。 @return 待运行任务 */ Map<String,Object> next();
 /** 标记正在派发。 @param id 任务 @return 修改数 */ int claim(@Param("id")String id);
 /** 统计尝试次数。 @param id 任务 @return 次数 */ int countAttempts(@Param("id")String id);
 /** 新建尝试。 @param row 尝试数据 @return 写入数 */ int startAttempt(Map<String,Object> row);
 /** 关联通话。 @param attempt 尝试 @param call 通话 @return 修改数 */ int attach(@Param("attempt")String attempt,@Param("call")String call);
 /** 查询需要对账的尝试。 @return 至多一百条运行尝试 */ List<Map<String,Object>> running();
 /** 原子回填一批已结束通话及所属任务，避免尝试结束后任务仍处于运行态。
  * @param ids 非空尝试标识集合，最多一百条
  * @return 更新行数（包括尝试和任务）
  */
 int reconcileBatch(@Param("ids") List<String> ids);
 /** 锁定仍在两分钟派发租期内的尝试；必须与保存通话意图处于同一事务。
  * @param id 尝试标识
  * @return 有效标识，失效返回空
  */
 String lockDispatch(@Param("id") String id);
 /** 查询无通话意图且派发租期已过的尝试。
  * @return 最多一百条标识
  */
 List<String> expiredDispatches();
 /** 原子结束未开始的过期派发与任务，不重拨未知通话。
  * @param ids 非空尝试标识集合
  * @return 更新行数
  */
 int expireDispatches(@Param("ids") List<String> ids);
 /** 通过持久通话意图恢复关联。 @param attempt 尝试 @return 通话事实 */ Map<String,Object> call(@Param("attempt")String attempt);
 /** 完成尝试。 @param id 尝试 @param status 状态 @param result 结果 @return 修改数 */ int finishAttempt(@Param("id")String id,@Param("status")String status,@Param("result")String result);
 /** 完成任务或安排重试。 @param id 任务 @param status 状态 @return 修改数 */ int finishJob(@Param("id")String id,@Param("status")String status);
 /** 用户暂停恢复取消。 @param tenant 租户 @param owner 坐席 @param id 任务 @param action 操作 @return 修改数 */ int control(@Param("tenant")long tenant,@Param("owner")String owner,@Param("id")String id,@Param("action")String action);
 /** 全局运行容量。 @return 正在运行的尝试数 */ int activeCount();
 /** 锁住调度容量。 @return 固定锁标识 */ Long schedulerLock();
 /** 每日号码频次。 @param tenant 租户 @param number 号码 @return 次数 */ int frequency(@Param("tenant")long tenant,@Param("number")String number);
}
