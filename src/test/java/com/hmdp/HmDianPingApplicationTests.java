package com.hmdp;

import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void idWorkerGeneratesUniqueIdsUnderConcurrency() throws Exception {
        String prefix = "test-order-" + UUID.randomUUID();
        int workers = 8;
        int idsPerWorker = 100;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch done = new CountDownLatch(workers);
        Set<Long> ids = ConcurrentHashMap.newKeySet();
        try {
            for (int i = 0; i < workers; i++) {
                executor.submit(() -> {
                    try {
                        for (int n = 0; n < idsPerWorker; n++) {
                            ids.add(redisIdWorker.nextId(prefix));
                        }
                    } finally {
                        done.countDown();
                    }
                });
            }
            assertTrue(done.await(10, TimeUnit.SECONDS));
            assertEquals(workers * idsPerWorker, ids.size());
        } finally {
            executor.shutdownNow();
            Set<String> keys = stringRedisTemplate.keys("icr:" + prefix + ":*");
            if (keys != null && !keys.isEmpty()) stringRedisTemplate.delete(keys);
        }
    }

    @Test
    void hyperLogLogEstimateIsCloseAndTestKeyIsRemoved() {
        String key = "test:hll:" + UUID.randomUUID();
        String[] values = new String[10000];
        for (int i = 0; i < values.length; i++) values[i] = "patient-" + i;
        try {
            stringRedisTemplate.opsForHyperLogLog().add(key, values);
            Long estimate = stringRedisTemplate.opsForHyperLogLog().size(key);
            assertTrue(estimate != null && estimate >= 9700 && estimate <= 10300,
                    "HyperLogLog estimate should be within a 3% error bound");
        } finally {
            stringRedisTemplate.delete(key);
        }
    }
}
