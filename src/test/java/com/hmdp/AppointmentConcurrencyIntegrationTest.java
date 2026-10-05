package com.hmdp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.entity.Schedule;
import com.hmdp.mapper.AppointmentMapper;
import com.hmdp.service.IScheduleService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AppointmentConcurrencyIntegrationTest {

    private static final int REQUESTS = 100;
    private static final int CONCURRENCY = 40;
    private static final int STOCK = 12;
    private static final String STREAM = "stream.appointments";
    private static final String GROUP = "g1";

    @LocalServerPort
    private int port;

    @Resource
    private StringRedisTemplate redis;

    @Resource
    private IScheduleService scheduleService;

    @Resource
    private AppointmentMapper appointmentMapper;

    @Resource
    private ObjectMapper objectMapper;

    @Test
    void concurrentHttpReservationsNeverExceedStockAndPersistConsistently() throws Exception {
        Schedule schedule = new Schedule()
                .setDoctorId(5L)
                .setScheduleDate(LocalDate.now().plusDays(400))
                .setShiftType(3)
                .setTotalCount(STOCK)
                .setAvailableCount(STOCK)
                .setBeginTime(LocalTime.of(18, 0))
                .setEndTime(LocalTime.of(21, 0));
        assertTrue(scheduleService.save(schedule));

        String stockKey = "schedule:stock:" + schedule.getId();
        String bookedKey = "schedule:booked:v2:" + schedule.getId();
        redis.opsForValue().set(stockKey, String.valueOf(STOCK), 2, TimeUnit.DAYS);

        List<String> tokens = new ArrayList<>();
        Set<Long> appointmentIds = Collections.synchronizedSet(new HashSet<>());
        List<Double> latencyMs = Collections.synchronizedList(new ArrayList<>());
        ExecutorService workers = Executors.newFixedThreadPool(CONCURRENCY);
        CountDownLatch ready = new CountDownLatch(CONCURRENCY);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ReservationResult>> futures = new ArrayList<>();
        long startedNanos = 0L;
        try {
            for (int i = 0; i < REQUESTS; i++) {
                String token = "load-" + UUID.randomUUID().toString().replace("-", "");
                tokens.add(token);
                Map<String, String> user = new HashMap<>();
                user.put("id", String.valueOf(8_000_000_000L + System.nanoTime() % 1_000_000_000L + i));
                user.put("nickName", "load-test");
                user.put("icon", "");
                redis.opsForHash().putAll("login:token:" + token, user);
                redis.expire("login:token:" + token, 10, TimeUnit.MINUTES);
                futures.add(workers.submit(() -> {
                    ready.countDown();
                    ready.await();
                    start.await();
                    HttpHeaders headers = new HttpHeaders();
                    headers.set("authorization", token);
                    long requestStarted = System.nanoTime();
                    ResponseEntity<Map> response = new RestTemplate().exchange(
                            "http://127.0.0.1:" + port + "/appointment/grab/" + schedule.getId(),
                            HttpMethod.POST, new HttpEntity<>(headers), Map.class);
                    latencyMs.add((System.nanoTime() - requestStarted) / 1_000_000.0);
                    if (response.getStatusCodeValue() != 200 || response.getBody() == null) {
                        return new ReservationResult(false, null, token, response.getStatusCodeValue());
                    }
                    Map body = response.getBody();
                    boolean accepted = Boolean.TRUE.equals(body.get("success"));
                    Long appointmentId = accepted ? ((Number) body.get("data")).longValue() : null;
                    if (appointmentId != null) appointmentIds.add(appointmentId);
                    return new ReservationResult(accepted, appointmentId, token, response.getStatusCodeValue());
                }));
            }

            assertTrue(ready.await(10, TimeUnit.SECONDS), "all reservation workers should be ready");
            startedNanos = System.nanoTime();
            start.countDown();
            int accepted = 0;
            int rejected = 0;
            ReservationResult ownerReservation = null;
            for (Future<ReservationResult> future : futures) {
                ReservationResult result = future.get(30, TimeUnit.SECONDS);
                assertEquals(200, result.httpStatus);
                if (result.accepted) {
                    accepted++;
                    if (ownerReservation == null) ownerReservation = result;
                }
                else rejected++;
            }
            long elapsedNanos = System.nanoTime() - startedNanos;

            assertEquals(STOCK, accepted, "accepted reservations must match available stock");
            assertEquals(REQUESTS - STOCK, rejected, "all excess reservations should be rejected");
            assertEquals(STOCK, appointmentIds.size(), "accepted appointment IDs must be unique");
            awaitPersisted(appointmentIds, 15, TimeUnit.SECONDS);
            assertEquals(0, scheduleService.getById(schedule.getId()).getAvailableCount());
            assertEquals("0", redis.opsForValue().get(stockKey));

            assertNotNull(ownerReservation);
            ResponseEntity<Map> ownRead = appointmentRead(ownerReservation.token, ownerReservation.appointmentId);
            assertEquals(200, ownRead.getStatusCodeValue());
            assertEquals(Boolean.TRUE, ownRead.getBody().get("success"));
            String ownerToken = ownerReservation.token;
            String otherPatientToken = tokens.stream().filter(token -> !token.equals(ownerToken)).findFirst().orElseThrow(AssertionError::new);
            ResponseEntity<Map> foreignRead = appointmentRead(otherPatientToken, ownerReservation.appointmentId);
            assertEquals(Boolean.FALSE, foreignRead.getBody().get("success"), "a different patient cannot read the appointment");

            writeReport(accepted, rejected, elapsedNanos, latencyMs);
        } finally {
            start.countDown();
            workers.shutdownNow();
            workers.awaitTermination(5, TimeUnit.SECONDS);
            cleanup(schedule, tokens, appointmentIds, stockKey, bookedKey);
        }
    }

    private ResponseEntity<Map> appointmentRead(String token, Long appointmentId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("authorization", token);
        return new RestTemplate().exchange("http://127.0.0.1:" + port + "/appointment/" + appointmentId,
                HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    }

    private void awaitPersisted(Set<Long> ids, long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (System.nanoTime() < deadline) {
            if (appointmentMapper.selectBatchIds(ids).size() == ids.size()) return;
            Thread.sleep(50L);
        }
        assertEquals(ids.size(), appointmentMapper.selectBatchIds(ids).size(), "all accepted reservations must persist");
    }

    private void writeReport(int accepted, int rejected, long elapsedNanos, List<Double> latencyMs) throws Exception {
        List<Double> sorted = new ArrayList<>(latencyMs);
        Collections.sort(sorted);
        Map<String, Object> latencies = new HashMap<>();
        latencies.put("mean", round(sorted.stream().mapToDouble(Double::doubleValue).average().orElse(0)));
        latencies.put("p50", percentile(sorted, 0.50));
        latencies.put("p95", percentile(sorted, 0.95));
        latencies.put("p99", percentile(sorted, 0.99));
        latencies.put("max", round(sorted.get(sorted.size() - 1)));
        Map<String, Object> report = new HashMap<>();
        report.put("started_at", OffsetDateTime.now().toString());
        report.put("target", "POST /appointment/grab/{temporaryScheduleId}");
        report.put("host", java.net.InetAddress.getLocalHost().getHostName());
        report.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version"));
        report.put("java", System.getProperty("java.version"));
        report.put("requests", REQUESTS);
        report.put("concurrency", CONCURRENCY);
        report.put("initial_stock", STOCK);
        report.put("accepted", accepted);
        report.put("business_rejected", rejected);
        report.put("http_errors", 0);
        report.put("elapsed_seconds", round(elapsedNanos / 1_000_000_000.0));
        report.put("throughput_requests_per_second", round(REQUESTS / (elapsedNanos / 1_000_000_000.0)));
        report.put("latency_ms", latencies);
        report.put("checks", Arrays.asList("accepted equals stock", "unique appointment IDs", "DB stock equals Redis stock", "all accepted rows persisted", "foreign appointment read denied"));
        report.put("note", "Single-host local integration benchmark with temporary data; not a production capacity claim.");
        Files.createDirectories(Paths.get("target", "performance"));
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(
                Paths.get("target", "performance", "appointment-concurrency.json").toFile(), report);
    }

    private void cleanup(Schedule schedule, List<String> tokens, Set<Long> appointmentIds,
                         String stockKey, String bookedKey) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline && appointmentMapper.selectBatchIds(appointmentIds).size() < appointmentIds.size()) {
            try { Thread.sleep(50L); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
        if (!appointmentIds.isEmpty()) appointmentMapper.deleteBatchIds(appointmentIds);
        for (MapRecord<String, Object, Object> record : redis.opsForStream().range(STREAM, Range.unbounded())) {
            Object id = record.getValue().get("id");
            if (id != null && appointmentIds.contains(Long.valueOf(id.toString()))) {
                redis.opsForStream().acknowledge(STREAM, GROUP, record.getId());
                redis.opsForStream().delete(STREAM, record.getId());
            }
        }
        for (String token : tokens) redis.delete("login:token:" + token);
        redis.delete(Arrays.asList(stockKey, bookedKey));
        scheduleService.removeById(schedule.getId());
    }

    private double percentile(List<Double> sorted, double ratio) {
        int index = Math.min(sorted.size() - 1, (int) Math.ceil(sorted.size() * ratio) - 1);
        return round(sorted.get(Math.max(0, index)));
    }

    private double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private static class ReservationResult {
        private final boolean accepted;
        private final Long appointmentId;
        private final String token;
        private final int httpStatus;

        private ReservationResult(boolean accepted, Long appointmentId, String token, int httpStatus) {
            this.accepted = accepted;
            this.appointmentId = appointmentId;
            this.token = token;
            this.httpStatus = httpStatus;
        }
    }
}
