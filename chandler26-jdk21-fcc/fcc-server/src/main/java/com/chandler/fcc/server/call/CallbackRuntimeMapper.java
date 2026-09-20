package com.chandler.fcc.server.call;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

/** 本租户待领取或本人回拨的查询与排他领取。 */
@Mapper
public interface CallbackRuntimeMapper {
    /** 分页读取可处理回拨摘要。
     * @param tenant 租户 @param owner 坐席 @param number 精确号码 @param offset 偏移 @return 最多十条
     */
    List<Map<String,Object>> list(@Param("tenant")long tenant,@Param("owner")String owner,@Param("number")String number,@Param("offset")int offset);
    /** 统计同权限同筛选范围。
     * @param tenant 租户 @param owner 坐席 @param number 精确号码 @return 总数
     */
    long count(@Param("tenant")long tenant,@Param("owner")String owner,@Param("number")String number);
    /** 锁定可处理记录并读取前次调度结果。
     * @param tenant 租户 @param owner 坐席 @param id 回拨标识 @return 锁定记录或空
     */
    Map<String,Object> lock(@Param("tenant")long tenant,@Param("owner")String owner,@Param("id")String id);
    /** 关联已在本事务创建的外呼任务。
     * @param tenant 租户 @param owner 坐席 @param id 回拨标识 @param job 外呼任务 @return 更新数
     */
    int schedule(@Param("tenant")long tenant,@Param("owner")String owner,@Param("id")String id,@Param("job")String job);
}
