package com.chandler.learning.agent.security;

import com.chandler.learning.agent.identity.domain.entity.LearningUser;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;

/**
 * 用户上下文二级内存缓存，避免在每个受保护请求的 JWT 过滤器中频繁查询数据库。
 */
@Slf4j
@Component
public class UserContextCache {

    private static final int DEFAULT_MAX_SIZE = 5000;
    private static final Duration DEFAULT_EXPIRE_DURATION = Duration.ofMinutes(60);

    private final Cache<Long, Optional<LearningUser>> cache;

    public UserContextCache() {
        this(DEFAULT_MAX_SIZE, DEFAULT_EXPIRE_DURATION);
    }

    public UserContextCache(int maxSize, Duration expireDuration) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(expireDuration)
                .build();
    }

    /**
     * 根据用户 ID 读取缓存，未命中时调用加载函数回源数据库。
     * <p>支持对 null 用户进行 Optional 空对象缓存，防止对无效用户 ID 穿透攻击。</p>
     *
     * @param userId 用户 ID
     * @param loader 数据加载器
     * @return 用户对象（可能为空）
     */
    public LearningUser get(Long userId, Function<Long, LearningUser> loader) {
        if (userId == null) {
            return null;
        }
        Optional<LearningUser> result = cache.get(userId, id -> {
            LearningUser loaded = loader != null ? loader.apply(id) : null;
            return Optional.ofNullable(loaded);
        });
        return result != null && result.isPresent() ? result.get() : null;
    }

    /**
     * 淘汰指定用户的缓存。
     *
     * @param userId 用户 ID
     */
    public void evict(Long userId) {
        if (userId != null) {
            cache.invalidate(userId);
            log.debug("用户上下文缓存已淘汰 userId={}", userId);
        }
    }

    /**
     * 清空全部用户缓存。
     */
    public void clear() {
        cache.invalidateAll();
        log.debug("用户上下文缓存已全量清空");
    }

    /**
     * 当前缓存条目数（供诊断监控使用）。
     */
    public long size() {
        return cache.estimatedSize();
    }
}
