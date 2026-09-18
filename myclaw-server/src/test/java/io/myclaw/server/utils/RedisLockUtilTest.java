package io.myclaw.server.utils;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RedisLockUtilTest {
    @Test
    void localFallbackDebouncesUntilWindowExpires() {
        RedisLockUtil locks = new RedisLockUtil(mock(StringRedisTemplate.class));
        assertThat(locks.tryLocalLock("key", 500, 1_000)).isTrue();
        assertThat(locks.tryLocalLock("key", 500, 1_499)).isFalse();
        assertThat(locks.tryLocalLock("key", 500, 1_500)).isTrue();
    }
}