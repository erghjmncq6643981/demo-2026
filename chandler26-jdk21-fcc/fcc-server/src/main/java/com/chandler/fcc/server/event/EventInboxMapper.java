package com.chandler.fcc.server.event;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 稳定事件收件箱，副作用前先保存事实。 */
@Mapper
public interface EventInboxMapper {
 /** 以唯一源事件领取。 @param id 源事件 @param node 节点 @param payload 报文 @return 插入数 */
 int receive(@Param("id")String id,@Param("node")String node,@Param("payload")String payload);
 /** 查询处理状态。 @param id 源事件 @return 状态 */ String status(@Param("id")String id);
 /** 持久处理结果。 @param id 源事件 @param status 状态 @return 修改数 */ int finish(@Param("id")String id,@Param("status")String status);
}
