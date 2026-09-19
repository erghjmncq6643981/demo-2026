package com.chandler.fcc.admin.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.fcc.admin.infrastructure.persistence.entity.AdminUserEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 管理控制台账号 Mapper (fcc_admin_user)
 * <p>
 * 提供控制台账号的 CRUD 能力。账号查询走单表条件构造器，无需自定义 SQL。
 * </p>
 *
 * @author Chandler
 */
@Mapper
public interface AdminUserMapper extends BaseMapper<AdminUserEntity> {
}
