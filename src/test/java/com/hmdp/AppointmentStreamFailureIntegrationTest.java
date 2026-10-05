package com.hmdp;

import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AppointmentStreamFailureIntegrationTest {

    private static final String STREAM = "stream.appointments";
    private static final String DLQ = "stream.appointments.dlq";

    @Resource
    private StringRedisTemplate redis;

    @Resource
    private RedissonClient redisson;

    @Test
    void lockContentionRetriesThenDeadLettersAndCompensatesExactlyOnce() throws Exception {
        long suffix = System.currentTimeMillis() * 1000L + (System.nanoTime() % 1000L);
        String patientId = String.valueOf(suffix);
        String appointmentId = String.valueOf(suffix + 1L);
        String scheduleId = "999999999";
        String stockKey = "schedule:stock:" + scheduleId;
        String bookedKey = "schedule:booked:v2:" + scheduleId;
        String lockKey = "lock:appointment:" + patientId;
        String compensatedKey = "appointment:compensated:" + appointmentId;
        RLock patientLock = redisson.getLock(lockKey);
        RecordId sourceId = null;
        String sentMarker = null;

        try {
            assertTrue(patientLock.tryLock(1, TimeUnit.SECONDS));
            redis.opsForValue().set(stockKey, "1");
            redis.opsForHash().put(bookedKey, patientId, appointmentId);
            Map<String, String> fields = new HashMap<>();
            fields.put("id", appointmentId);
            fields.put("patientId", patientId);
            fields.put("scheduleId", scheduleId);
            sourceId = redis.opsForStream().add(STREAM, fields);
            assertNotNull(sourceId);
            sentMarker = "appointment:dlq:sent:" + sourceId.getValue();

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
            while (System.nanoTime() < deadline && !Boolean.TRUE.equals(redis.hasKey(sentMarker))) {
                Thread.sleep(50L);
            }
            assertTrue(Boolean.TRUE.equals(redis.hasKey(sentMarker)), "message should reach the DLQ after retries");
            while (System.nanoTime() < deadline && !"2".equals(redis.opsForValue().get(stockKey))) {
                Thread.sleep(25L);
            }
            assertEquals("2", redis.opsForValue().get(stockKey), "the reserved Redis stock is compensated once");
            assertNull(redis.opsForHash().get(bookedKey, patientId), "the failed reservation is removed");
            String dlqId = redis.opsForValue().get(sentMarker);
            assertNotNull(dlqId, "the source message should have a deduplicated DLQ marker");
            List<MapRecord<String, Object, Object>> deadLetters = redis.opsForStream().range(DLQ, Range.unbounded());
            MapRecord<String, Object, Object> deadLetter = deadLetters.stream()
                    .filter(item -> item.getId().getValue().equals(dlqId))
                    .findFirst().orElse(null);
            assertNotNull(deadLetter, "the DLQ marker should point to a persisted dead-letter record");
            assertEquals("5", String.valueOf(deadLetter.getValue().get("attempts")));
            assertNull(redis.opsForValue().get("appointment:retry:" + sourceId.getValue()),
                    "retry counter is temporary and is cleared after terminal handling");
        } finally {
            if (patientLock.isHeldByCurrentThread()) patientLock.unlock();
            if (sourceId != null) {
                String marker = "appointment:dlq:sent:" + sourceId.getValue();
                String dlqId = redis.opsForValue().get(marker);
                if (dlqId != null) redis.opsForStream().delete(DLQ, RecordId.of(dlqId));
                redis.opsForStream().delete(STREAM, sourceId);
                redis.delete("appointment:retry:" + sourceId.getValue());
                redis.delete(marker);
            }
            redis.opsForHash().delete(bookedKey, patientId);
            redis.delete(Arrays.asList(stockKey, compensatedKey));
        }
    }
}
