package com.chandler.fcc.server.websocket.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/** 弹屏投递、过期及终端展示回执事实。 */
@Mapper
public interface ScreenPopDeliveryMapper {
    /** 首次插入，重复推送不能延长期限。
     * @param owner 坐席 @param call 通话 @param payload 封套 @param expires 过期毫秒
     * @return 写入行数
     */
    int save(@Param("owner") String owner,@Param("call") String call,
             @Param("payload") String payload,@Param("expires") long expires);
    /** 查询本人有效提醒。
     * @param owner 坐席 @return 最多二十条封套
     */
    List<String> pending(@Param("owner") String owner);
    /** 幂等记录回执，各阶段时间独立存储。
     * @param owner 坐席 @param call 通话 @param state 回执类型 @return 更新数
     */
    int receipt(@Param("owner") String owner,@Param("call") String call,@Param("state") String state);
    /** 关闭提醒，不覆盖已收回执。
     * @param owner 坐席 @param call 通话 @return 更新数
     */
    int close(@Param("owner") String owner,@Param("call") String call);
}
