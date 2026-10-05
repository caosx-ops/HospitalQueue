package com.hmdp.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import javax.annotation.PreDestroy;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.CACHE_NULL_TTL;
import static com.hmdp.utils.RedisConstants.LOCK_DOCTOR_KEY;

@Slf4j
@Component
public class CacheClient {

    private static final int MISS_LOCK_ATTEMPTS = 20;
    private static final long LOCK_RETRY_MILLIS = 25L;
    private final ExecutorService cacheRebuildExecutor = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "doctor-cache-rebuild");
        thread.setDaemon(true);
        return thread;
    });
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @PreDestroy
    public void shutdown() {
        cacheRebuildExecutor.shutdownNow();
    }

    public void set(String key, Object value, Long time, TimeUnit unit) {
        long jitter = ThreadLocalRandom.current().nextLong(Math.max(1L, time / 10L + 1L));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time + jitter, unit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        long jitter = ThreadLocalRandom.current().nextLong(Math.max(1L, time / 10L + 1L));
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time + jitter)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <R, ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }
        if (json != null) {
            return null;
        }

        R value = dbFallback.apply(id);
        if (value == null) {
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        set(key, value, time, unit);
        return value;
    }

    public <R, ID> R queryWithLogicalExpire(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json == null) {
            return loadOnMiss(key, id, type, dbFallback, time, unit);
        }
        if (StrUtil.isBlank(json)) {
            return null;
        }

        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R value = JSONUtil.toBean(JSONUtil.toJsonStr(redisData.getData()), type);
        if (redisData.getExpireTime() != null && redisData.getExpireTime().isAfter(LocalDateTime.now())) {
            return value;
        }

        String lockKey = LOCK_DOCTOR_KEY + id;
        String lockToken = tryLock(lockKey);
        if (lockToken != null) {
            try {
                cacheRebuildExecutor.submit(() -> {
                    try {
                        R refreshed = dbFallback.apply(id);
                        if (refreshed == null) {
                            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                        } else {
                            setWithLogicalExpire(key, refreshed, time, unit);
                        }
                    } catch (Exception e) {
                        log.warn("逻辑过期缓存重建失败，key={}", key, e);
                    } finally {
                        unlock(lockKey, lockToken);
                    }
                });
            } catch (RuntimeException e) {
                unlock(lockKey, lockToken);
                log.warn("缓存重建任务提交失败，key={}", key, e);
            }
        }
        return value;
    }

    public <R, ID> R queryWithMutex(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        for (int attempt = 0; attempt < MISS_LOCK_ATTEMPTS; attempt++) {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(json)) {
                return JSONUtil.toBean(json, type);
            }
            if (json != null) {
                return null;
            }

            String lockKey = LOCK_DOCTOR_KEY + id;
            String lockToken = tryLock(lockKey);
            if (lockToken != null) {
                try {
                    json = stringRedisTemplate.opsForValue().get(key);
                    if (StrUtil.isNotBlank(json)) {
                        return JSONUtil.toBean(json, type);
                    }
                    if (json != null) {
                        return null;
                    }
                    R value = dbFallback.apply(id);
                    if (value == null) {
                        stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                    } else {
                        set(key, value, time, unit);
                    }
                    return value;
                } finally {
                    unlock(lockKey, lockToken);
                }
            }
            pauseBeforeRetry();
        }
        return dbFallback.apply(id);
    }

    private <R, ID> R loadOnMiss(
            String key, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String lockKey = LOCK_DOCTOR_KEY + id;
        for (int attempt = 0; attempt < MISS_LOCK_ATTEMPTS; attempt++) {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(json)) {
                RedisData redisData = JSONUtil.toBean(json, RedisData.class);
                return JSONUtil.toBean(JSONUtil.toJsonStr(redisData.getData()), type);
            }
            if (json != null) {
                return null;
            }

            String lockToken = tryLock(lockKey);
            if (lockToken != null) {
                try {
                    json = stringRedisTemplate.opsForValue().get(key);
                    if (StrUtil.isNotBlank(json)) {
                        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
                        return JSONUtil.toBean(JSONUtil.toJsonStr(redisData.getData()), type);
                    }
                    if (json != null) {
                        return null;
                    }
                    R value = dbFallback.apply(id);
                    if (value == null) {
                        stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                    } else {
                        setWithLogicalExpire(key, value, time, unit);
                    }
                    return value;
                } finally {
                    unlock(lockKey, lockToken);
                }
            }
            pauseBeforeRetry();
        }
        return dbFallback.apply(id);
    }

    private String tryLock(String key) {
        String token = UUID.randomUUID().toString();
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(key, token, 10, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(acquired) ? token : null;
    }

    private void unlock(String key, String token) {
        stringRedisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(key), token);
    }

    private void pauseBeforeRetry() {
        try {
            Thread.sleep(LOCK_RETRY_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待缓存锁时线程被中断", e);
        }
    }
}
