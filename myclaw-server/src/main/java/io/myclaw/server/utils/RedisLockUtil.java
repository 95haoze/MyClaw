package io.myclaw.server.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
public class RedisLockUtil {
    private static final Logger log = LoggerFactory.getLogger(RedisLockUtil.class);
    private final StringRedisTemplate redisTemplate;
    private final ConcurrentHashMap<String, Long> localLocks = new ConcurrentHashMap<>();

    public RedisLockUtil(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean tryLock(String key, long expireMs) {
        if (expireMs <= 0) throw new IllegalArgumentException("Lock expiration must be greater than zero");
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, "1", expireMs, TimeUnit.MILLISECONDS);
            return Boolean.TRUE.equals(acquired);
        } catch (RuntimeException redisFailure) {
            log.warn("Redis 不可用，防重复提交已降级为本机内存锁", redisFailure);
            return tryLocalLock(key, expireMs, System.currentTimeMillis());
        }
    }

    boolean tryLocalLock(String key, long expireMs, long now) {
        long expiresAt = Math.addExact(now, expireMs);
        final boolean[] acquired = {false};
        localLocks.compute(key, (ignored, currentExpiry) -> {
            if (currentExpiry == null || currentExpiry <= now) {
                acquired[0] = true;
                return expiresAt;
            }
            return currentExpiry;
        });
        if (localLocks.size() > 10_000) localLocks.entrySet().removeIf(entry -> entry.getValue() <= now);
        return acquired[0];
    }
}