package com.chandler.fcc.server.customer.infrastructure;

import com.chandler.fcc.server.customer.api.CustomerRecord;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 客户资料持久化端口，坐席入口始终按负责工号限制访问范围。
 */
@Mapper
public interface CustomerMapper {

    /**
     * 查询当前负责坐席的客户摘要。
     *
     * @param owner 负责坐席工号
     * @param phone 规范化号码，可为空
     * @param offset 分页偏移
     * @param limit 分页上限
     * @return 不含备注的客户摘要
     */
    List<CustomerRecord> list(
        @Param("owner") String owner,
        @Param("phone") String phone,
        @Param("offset") int offset,
        @Param("limit") int limit
    );

    /**
     * 查询当前负责坐席的一位客户。
     *
     * @param owner 负责坐席工号
     * @param id 客户标识
     * @return 客户详情，不存在时为空
     */
    CustomerRecord detail(@Param("owner") String owner, @Param("id") String id);

    /**
     * 新增客户。
     *
     * @param owner 负责坐席工号
     * @param customer 客户资料
     * @return 插入数量
     */
    int insert(@Param("owner") String owner, @Param("customer") CustomerRecord customer);

    /**
     * 按乐观版本修改客户。
     *
     * @param owner 负责坐席工号
     * @param customer 客户资料
     * @return 更新数量
     */
    int update(@Param("owner") String owner, @Param("customer") CustomerRecord customer);
}
