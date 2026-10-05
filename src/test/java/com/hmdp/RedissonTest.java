package com.hmdp;

import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RedissonTest {

    @Resource
    private RedissonClient redissonClient;

    @Test
    void lockIsReentrantForItsOwningThread() {
        RLock lock = redissonClient.getLock("test:reentrant:" + UUID.randomUUID());
        assertTrue(lock.tryLock());
        try {
            assertTrue(lock.tryLock());
            assertTrue(lock.isHeldByCurrentThread());
            assertEquals(2, lock.getHoldCount());
        } finally {
            while (lock.isHeldByCurrentThread()) lock.unlock();
        }
    }

    @Test
    void lockExcludesACompetingThread() throws Exception {
        RLock lock = redissonClient.getLock("test:exclusive:" + UUID.randomUUID());
        ExecutorService executor = Executors.newSingleThreadExecutor();
        assertTrue(lock.tryLock());
        try {
            Future<Boolean> acquired = executor.submit(() -> lock.tryLock(100, TimeUnit.MILLISECONDS));
            assertFalse(acquired.get(2, TimeUnit.SECONDS));
        } finally {
            lock.unlock();
            executor.shutdownNow();
        }
    }
}
