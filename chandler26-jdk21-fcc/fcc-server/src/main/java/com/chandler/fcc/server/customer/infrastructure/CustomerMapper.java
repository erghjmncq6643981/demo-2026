package com.chandler.fcc.server.customer.infrastructure;

import com.chandler.fcc.server.customer.api.CustomerRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/** 客户资料持久化，所有查询均限定租户和负责坐席。 */
@Mapper
public interface CustomerMapper {
    /** 查询客户摘要。
     * @param tenantId 租户 @param owner 负责坐席 @param phone 规范化号码，可空 @param offset 偏移 @param limit 上限
     * @return 不含备注的摘要
     */
    List<CustomerRecord> list(@Param("tenantId") long tenantId, @Param("owner") String owner, @Param("phone") String phone, @Param("offset") int offset, @Param("limit") int limit);
    /** 查询一位客户。
     * @param tenantId 租户 @param owner 负责坐席 @param id 客户标识 @return 客户详情或空
     */
    CustomerRecord detail(@Param("tenantId") long tenantId, @Param("owner") String owner, @Param("id") String id);
    /** 新增客户。
     * @param tenantId 租户 @param owner 负责坐席 @param customer 客户资料 @return 写入数
     */
    int insert(@Param("tenantId") long tenantId, @Param("owner") String owner, @Param("customer") CustomerRecord customer);
    /** 按版本修改客户。
     * @param tenantId 租户 @param owner 负责坐席 @param customer 客户资料 @return 修改数
     */
    int update(@Param("tenantId") long tenantId, @Param("owner") String owner, @Param("customer") CustomerRecord customer);
}
