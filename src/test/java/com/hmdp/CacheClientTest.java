package com.hmdp;

import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Doctor;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CacheClientTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final CacheClient cacheClient = new CacheClient(redis);

    CacheClientTest() {
        when(redis.opsForValue()).thenReturn(values);
        doReturn(1L).when(redis).execute(any(RedisScript.class), anyList(), any());
    }

    @AfterEach
    void closeExecutor() {
        cacheClient.shutdown();
    }

    @Test
    void freshLogicalCacheDoesNotReadDatabase() {
        when(values.get("cache:doctor:7")).thenReturn(logicalValue(doctor(7L, "cached"), LocalDateTime.now().plusMinutes(2)));

        Doctor result = cacheClient.queryWithLogicalExpire("cache:doctor:", 7L, Doctor.class,
                id -> fail("fresh cache must not query the database"), 30L, TimeUnit.MINUTES);

        assertEquals("cached", result.getName());
        verify(values, never()).setIfAbsent(anyString(), anyString(), anyLong(), any());
    }

    @Test
    void cacheMissLoadsOnceAndStoresLogicalExpiry() {
        when(values.get("cache:doctor:8")).thenReturn(null, null);
        when(values.setIfAbsent(eq("lock:doctor:8"), anyString(), eq(10L), eq(TimeUnit.SECONDS))).thenReturn(true);
        AtomicInteger databaseCalls = new AtomicInteger();

        Doctor result = cacheClient.queryWithLogicalExpire("cache:doctor:", 8L, Doctor.class,
                id -> { databaseCalls.incrementAndGet(); return doctor(id, "database"); }, 30L, TimeUnit.MINUTES);

        assertEquals("database", result.getName());
        assertEquals(1, databaseCalls.get());
        verify(values).set(eq("cache:doctor:8"), argThat(json -> {
            RedisData data = JSONUtil.toBean(json, RedisData.class);
            Doctor cached = JSONUtil.toBean(JSONUtil.toJsonStr(data.getData()), Doctor.class);
            return cached.getId().equals(8L) && data.getExpireTime().isAfter(LocalDateTime.now());
        }));
    }

    @Test
    void expiredHotCacheReturnsStaleValueAndOnlySubmitsOneRebuild() throws Exception {
        String stale = logicalValue(doctor(9L, "stale"), LocalDateTime.now().minusSeconds(1));
        when(values.get("cache:doctor:9")).thenReturn(stale, stale);
        when(values.setIfAbsent(eq("lock:doctor:9"), anyString(), eq(10L), eq(TimeUnit.SECONDS)))
                .thenReturn(true, false);
        CountDownLatch rebuilt = new CountDownLatch(1);
        AtomicInteger databaseCalls = new AtomicInteger();
        java.util.function.Function<Long, Doctor> loader = id -> {
            databaseCalls.incrementAndGet();
            rebuilt.countDown();
            return doctor(id, "fresh");
        };

        Doctor first = cacheClient.queryWithLogicalExpire("cache:doctor:", 9L, Doctor.class,
                loader, 30L, TimeUnit.MINUTES);
        Doctor second = cacheClient.queryWithLogicalExpire("cache:doctor:", 9L, Doctor.class,
                loader, 30L, TimeUnit.MINUTES);

        assertEquals("stale", first.getName());
        assertEquals("stale", second.getName());
        assertTrue(rebuilt.await(2, TimeUnit.SECONDS));
        assertEquals(1, databaseCalls.get());
    }

    private Doctor doctor(Long id, String name) {
        return new Doctor().setId(id).setName(name);
    }

    private String logicalValue(Doctor doctor, LocalDateTime expires) {
        RedisData data = new RedisData();
        data.setData(doctor);
        data.setExpireTime(expires);
        return JSONUtil.toJsonStr(data);
    }
}
