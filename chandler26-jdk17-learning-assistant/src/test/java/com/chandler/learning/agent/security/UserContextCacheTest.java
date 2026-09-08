package com.chandler.learning.agent.security;

import com.chandler.learning.agent.identity.domain.entity.LearningUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("用户上下文内存缓存测试")
class UserContextCacheTest {

    private UserContextCache cache;

    @BeforeEach
    void setUp() {
        cache = new UserContextCache(100, Duration.ofMinutes(5));
    }

    @Test
    void cachesUserOnCacheMissAndReusesOnHit() {
        AtomicInteger loaderCalls = new AtomicInteger(0);
        Long userId = 1001L;

        LearningUser user1 = cache.get(userId, id -> {
            loaderCalls.incrementAndGet();
            LearningUser u = new LearningUser();
            u.setId(id);
            u.setUsername("chandler");
            return u;
        });

        assertThat(user1).isNotNull();
        assertThat(user1.getUsername()).isEqualTo("chandler");
        assertThat(loaderCalls.get()).isEqualTo(1);

        // 第二次查询命中缓存，不应调用 loader
        LearningUser user2 = cache.get(userId, id -> {
            loaderCalls.incrementAndGet();
            return null;
        });

        assertThat(user2).isSameAs(user1);
        assertThat(loaderCalls.get()).isEqualTo(1);
    }

    @Test
    void cachesNullUserToPreventCachePenetration() {
        AtomicInteger loaderCalls = new AtomicInteger(0);
        Long nonExistentId = 99999L;

        LearningUser user1 = cache.get(nonExistentId, id -> {
            loaderCalls.incrementAndGet();
            return null;
        });

        assertThat(user1).isNull();
        assertThat(loaderCalls.get()).isEqualTo(1);

        // 第二次查询相同不存在的 ID，直接返回 null，不回源
        LearningUser user2 = cache.get(nonExistentId, id -> {
            loaderCalls.incrementAndGet();
            return null;
        });

        assertThat(user2).isNull();
        assertThat(loaderCalls.get()).isEqualTo(1);
    }

    @Test
    void evictsSpecifiedUserFromCache() {
        AtomicInteger loaderCalls = new AtomicInteger(0);
        Long userId = 2002L;

        cache.get(userId, id -> {
            loaderCalls.incrementAndGet();
            LearningUser u = new LearningUser();
            u.setId(id);
            return u;
        });
        assertThat(loaderCalls.get()).isEqualTo(1);

        // 淘汰缓存
        cache.evict(userId);

        // 再次获取应重新调用 loader
        cache.get(userId, id -> {
            loaderCalls.incrementAndGet();
            LearningUser u = new LearningUser();
            u.setId(id);
            return u;
        });
        assertThat(loaderCalls.get()).isEqualTo(2);
    }

    @Test
    void clearsAllCachedEntries() {
        cache.get(1L, id -> new LearningUser());
        cache.get(2L, id -> new LearningUser());
        assertThat(cache.size()).isEqualTo(2);

        cache.clear();
        assertThat(cache.size()).isEqualTo(0);
    }
}
