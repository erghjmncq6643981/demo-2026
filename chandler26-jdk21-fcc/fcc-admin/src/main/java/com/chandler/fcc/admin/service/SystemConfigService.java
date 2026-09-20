package com.chandler.fcc.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.fcc.admin.infrastructure.persistence.entity.SystemConfigEntity;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.SystemConfigMapper;
import com.chandler.fcc.admin.model.dto.SystemConfigReq;
import com.chandler.fcc.admin.model.vo.SystemConfigVO;
import com.chandler.fcc.common.util.IdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 动态系统业务配置服务
 * <p>
 * 支持前端 WEB、后台 BACKEND、客户端 CLIENT 及系统 SYSTEM 多作用域参数动态配置与热加载。
 * </p>
 *
 * @author Chandler
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemConfigService {

    private static final String CONFIG_REDIS_PREFIX = "fcc:config:";

    private final SystemConfigMapper configMapper;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 保存或更新配置项
     *
     * @param req      配置修改入参
     * @param operator 当前操作人
     * @return 配置项主键 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long saveOrUpdateConfig(SystemConfigReq req, String operator) {
        String scope = req.getScope() == null ? "BACKEND" : req.getScope().trim().toUpperCase();
        String propName = req.getPropName().trim();

        LambdaQueryWrapper<SystemConfigEntity> wrapper = new LambdaQueryWrapper<SystemConfigEntity>()
                .eq(SystemConfigEntity::getScope, scope)
                .eq(SystemConfigEntity::getPropName, propName);

        SystemConfigEntity entity = configMapper.selectOne(wrapper);
        LocalDateTime now = LocalDateTime.now();

        if (entity == null) {
            entity = SystemConfigEntity.builder()
                    .id(IdUtil.nextId())
                    .propName(propName)
                    .propValue(req.getPropValue())
                    .propType(req.getPropType() == null ? "STRING" : req.getPropType().trim())
                    .scope(scope)
                    .description(req.getDescription())
                    .createdBy(operator)
                    .updatedBy(operator)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            configMapper.insert(entity);
            log.info("[SystemConfigService] 新增系统配置: scope={}, key={}, operator={}", scope, propName, operator);
        } else {
            entity.setPropValue(req.getPropValue());
            if (req.getPropType() != null) {
                entity.setPropType(req.getPropType());
            }
            if (req.getDescription() != null) {
                entity.setDescription(req.getDescription());
            }
            entity.setUpdatedBy(operator);
            entity.setUpdatedAt(now);
            configMapper.updateById(entity);
            log.info("[SystemConfigService] 更新系统配置: scope={}, key={}, operator={}", scope, propName, operator);
        }

        // 缓存到 Redis
        try {
            if (stringRedisTemplate != null) {
                stringRedisTemplate.opsForValue().set(CONFIG_REDIS_PREFIX + scope + ":" + propName, req.getPropValue());
            }
        } catch (Exception e) {
            log.warn("[SystemConfigService] 同步 Redis 缓存失败: {}", e.getMessage());
        }

        return entity.getId();
    }

    /**
     * 根据作用域查询配置项列表
     *
     * @param scope 作用域 (WEB, BACKEND, CLIENT, SYSTEM)
     * @return 配置展示视图列表
     */
    public List<SystemConfigVO> listConfigsByScope(String scope) {
        LambdaQueryWrapper<SystemConfigEntity> wrapper = new LambdaQueryWrapper<SystemConfigEntity>()
                .eq(scope != null && !scope.isBlank(), SystemConfigEntity::getScope, scope)
                .orderByAsc(SystemConfigEntity::getPropName);

        List<SystemConfigEntity> list = configMapper.selectList(wrapper);
        return list.stream().map(c -> SystemConfigVO.builder()
                .id(c.getId())
                .propName(c.getPropName())
                .propValue(c.getPropValue())
                .propType(c.getPropType())
                .scope(c.getScope())
                .description(c.getDescription())
                .updatedBy(c.getUpdatedBy())
                .updatedAt(c.getUpdatedAt())
                .build()
        ).toList();
    }

    /**
     * 删除指定配置项
     *
     * @param id 配置主键 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteConfig(Long id) {
        SystemConfigEntity entity = configMapper.selectById(id);
        if (entity != null) {
            configMapper.deleteById(id);
            try {
                if (stringRedisTemplate != null) {
                    stringRedisTemplate.delete(CONFIG_REDIS_PREFIX + entity.getScope() + ":" + entity.getPropName());
                }
            } catch (Exception e) {
                log.warn("[SystemConfigService] 清理 Redis 配置缓存异常: {}", e.getMessage());
            }
            log.info("[SystemConfigService] 成功删除系统配置: id={}", id);
        }
    }
}
