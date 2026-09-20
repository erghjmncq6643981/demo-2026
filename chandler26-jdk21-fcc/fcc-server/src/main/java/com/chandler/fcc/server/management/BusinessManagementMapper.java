package com.chandler.fcc.server.management;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

/** 管理端业务摘要查询，租户范围由在线认证提供，列表有硬上限。 */
@Mapper
public interface BusinessManagementMapper {
    /** 查询客户摘要。 @param tenant 租户 @param owner 坐席筛选 @param phone 号码筛选 @param offset 偏移 @return 摘要 */
    List<Map<String,Object>> customers(@Param("tenant") long tenant,@Param("owner") String owner,@Param("phone") String phone,@Param("offset") int offset);
    /** 查询任务摘要。 @param tenant 租户 @param owner 坐席筛选 @param offset 偏移 @return 摘要 */
    List<Map<String,Object>> jobs(@Param("tenant") long tenant,@Param("owner") String owner,@Param("offset") int offset);
    /** 查询本租户资源的负责坐席。 @param tenant 租户 @param id 资源 @return 负责工号 */
    String customerOwner(@Param("tenant") long tenant,@Param("id") String id);
    /** 查询本租户任务负责坐席。 @param tenant 租户 @param id 任务 @return 负责工号 */
    String jobOwner(@Param("tenant") long tenant,@Param("id") String id);
}
