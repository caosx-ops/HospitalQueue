package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Appointment;
import com.hmdp.entity.Schedule;
import com.hmdp.mapper.AppointmentMapper;
import com.hmdp.service.IAppointmentService;
import com.hmdp.service.IScheduleService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.service.impl.AppointmentOrderPersistenceService.PermanentAppointmentException;

@Slf4j
@Service
public class AppointmentServiceImpl extends ServiceImpl<AppointmentMapper, Appointment> implements IAppointmentService {

    private static final String STREAM_KEY = "stream.appointments";
    private static final String DLQ_STREAM_KEY = "stream.appointments.dlq";
    private static final String GROUP = "g1";
    private static final String CONSUMER = "c1";
    private static final long RETRY_TTL_DAYS = 30L;
    private static final AppointmentRetryPolicy RETRY_POLICY = new AppointmentRetryPolicy(5, 100L, 2000L);

    private static final DefaultRedisScript<Long> GRAB_SCHEDULE_SCRIPT = script("grab_schedule.lua");
    private static final DefaultRedisScript<Long> COMPENSATE_SCRIPT = script("compensate_schedule.lua");
    private static final DefaultRedisScript<Long> PUBLISH_DLQ_SCRIPT = script("publish_appointment_dlq.lua");

    private final ExecutorService orderExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "appointment-stream-consumer");
        thread.setDaemon(true);
        return thread;
    });

    @Resource
    private IScheduleService scheduleService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private AppointmentOrderPersistenceService persistenceService;

    @Resource
    private AppointmentMetrics appointmentMetrics;

    private static DefaultRedisScript<Long> script(String classpathResource) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(classpathResource));
        script.setResultType(Long.class);
        return script;
    }

    @PostConstruct
    private void init() {
        initStreamAndStock();
        orderExecutor.submit(this::consumeLoop);
    }

    @PreDestroy
    private void shutdown() {
        orderExecutor.shutdownNow();
    }

    private void initStreamAndStock() {
        try {
            if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(STREAM_KEY))) {
                stringRedisTemplate.opsForStream().add(STREAM_KEY, Collections.singletonMap("bootstrap", "1"));
            }
            try {
                stringRedisTemplate.opsForStream().createGroup(STREAM_KEY, ReadOffset.from("0-0"), GROUP);
            } catch (Exception e) {
                if (e.getMessage() == null || !e.getMessage().contains("BUSYGROUP")) {
                    throw e;
                }
            }
            scheduleService.list().forEach(schedule -> stringRedisTemplate.opsForValue()
                    .setIfAbsent(stockKey(schedule.getId()), String.valueOf(schedule.getAvailableCount()), 2, TimeUnit.DAYS));
            persistenceService.listActiveAppointments().forEach(appointment -> stringRedisTemplate.opsForHash()
                    .put(bookedKey(appointment.getScheduleId()), String.valueOf(appointment.getPatientId()),
                            String.valueOf(appointment.getId())));
        } catch (Exception e) {
            log.warn("预约队列初始化失败，消费者会继续尝试读取", e);
        }
    }

    private void consumeLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                List<MapRecord<String, Object, Object>> pending = stringRedisTemplate.opsForStream().read(
                        Consumer.from(GROUP, CONSUMER), StreamReadOptions.empty().count(1),
                        StreamOffset.create(STREAM_KEY, ReadOffset.from("0-0")));
                if (pending != null && !pending.isEmpty()) {
                    processRecord(pending.get(0));
                    continue;
                }

                List<MapRecord<String, Object, Object>> fresh = stringRedisTemplate.opsForStream().read(
                        Consumer.from(GROUP, CONSUMER),
                        StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                        StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()));
                if (fresh != null && !fresh.isEmpty()) {
                    processRecord(fresh.get(0));
                }
            } catch (Exception e) {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                log.error("预约 Stream 消费循环异常", e);
                sleepQuietly(1000L);
            }
        }
    }

    private void processRecord(MapRecord<String, Object, Object> record) {
        Map<Object, Object> fields = record.getValue();
        if (fields.containsKey("bootstrap")) {
            acknowledge(record.getId());
            return;
        }

        String appointmentId = value(fields, "id");
        String patientId = value(fields, "patientId");
        String scheduleId = value(fields, "scheduleId");
        if (appointmentId == null || patientId == null || scheduleId == null) {
            try {
                publishDeadLetter(record.getId().getValue(), appointmentId, patientId, scheduleId, 0,
                        "预约消息缺少必需字段");
                acknowledge(record.getId());
            } catch (Exception e) {
                log.error("无效预约消息转入死信队列失败，id={}", record.getId(), e);
                sleepQuietly(1000L);
            }
            return;
        }

        try {
            Long.parseLong(appointmentId);
            Long.parseLong(patientId);
            Long.parseLong(scheduleId);
        } catch (NumberFormatException e) {
            try {
                publishDeadLetter(record.getId().getValue(), appointmentId, patientId, scheduleId, 0,
                        "预约消息中的ID格式无效");
                acknowledge(record.getId());
            } catch (Exception publishError) {
                log.error("格式错误的预约消息转入死信队列失败，id={}", record.getId(), publishError);
                sleepQuietly(1000L);
            }
            return;
        }

        Appointment appointment = BeanUtil.fillBeanWithMap(fields, new Appointment(), true);
        String retryKey = retryKey(record.getId());
        try {
            persistenceService.persist(appointment);
            appointmentMetrics.recordPersisted();
            acknowledge(record.getId());
            stringRedisTemplate.delete(retryKey);
        } catch (PermanentAppointmentException e) {
            failPermanently(record, appointment, retryKey, e.getMessage());
        } catch (Exception e) {
            Long attempt = stringRedisTemplate.opsForValue().increment(retryKey);
            appointmentMetrics.recordRetry();
            stringRedisTemplate.expire(retryKey, RETRY_TTL_DAYS, TimeUnit.DAYS);
            int retryCount = attempt == null ? 1 : attempt.intValue();
            if (RETRY_POLICY.exhausted(retryCount)) {
                failPermanently(record, appointment, retryKey,
                        "超过重试上限: " + rootMessage(e));
                return;
            }
            long backoffMillis = RETRY_POLICY.delayAfterFailure(retryCount);
            log.warn("预约落库失败，将退避后重试，streamId={} failedAttempts={}/{} delay={}ms",
                    record.getId(), retryCount, RETRY_POLICY.getMaxRetries(), backoffMillis, e);
            sleepQuietly(backoffMillis);
        }
    }

    private void failPermanently(MapRecord<String, Object, Object> record, Appointment appointment,
                                 String retryKey, String reason) {
        try {
            String appointmentId = String.valueOf(appointment.getId());
            String patientId = String.valueOf(appointment.getPatientId());
            String scheduleId = String.valueOf(appointment.getScheduleId());
            String retryValue = stringRedisTemplate.opsForValue().get(retryKey);
            int attempts = retryValue == null ? 0 : Integer.parseInt(retryValue);

            publishDeadLetter(record.getId().getValue(), appointmentId, patientId, scheduleId, attempts, reason);
            compensateSchedule(appointment);
            acknowledge(record.getId());
            stringRedisTemplate.delete(retryKey);
            log.error("预约消息已转入死信并完成号源补偿，streamId={} appointmentId={} reason={}",
                    record.getId(), appointmentId, reason);
        } catch (Exception e) {
            log.error("预约死信处置未完成，消息保留在 pending 以便后续重试，streamId={}", record.getId(), e);
            sleepQuietly(1000L);
        }
    }

    private void publishDeadLetter(String sourceId, String appointmentId, String patientId, String scheduleId,
                                   int attempts, String reason) {
        List<String> keys = Arrays.asList(DLQ_STREAM_KEY, "appointment:dlq:sent:" + sourceId);
        Long published = stringRedisTemplate.execute(PUBLISH_DLQ_SCRIPT, keys,
                "sourceId", sourceId,
                "appointmentId", nullToEmpty(appointmentId),
                "patientId", nullToEmpty(patientId),
                "scheduleId", nullToEmpty(scheduleId),
                "attempts", String.valueOf(attempts),
                "reason", reason == null ? "unknown" : reason);
        if (published == null) {
            throw new IllegalStateException("死信写入未确认");
        }
        if (published == 1L) appointmentMetrics.recordDeadLetter();
    }

    private void compensateSchedule(Appointment appointment) {
        Long compensated = stringRedisTemplate.execute(COMPENSATE_SCRIPT,
                Arrays.asList(stockKey(appointment.getScheduleId()), bookedKey(appointment.getScheduleId()),
                        "appointment:compensated:" + appointment.getId()),
                String.valueOf(appointment.getPatientId()), String.valueOf(appointment.getId()));
        if (compensated != null && compensated == 1L) appointmentMetrics.recordCompensation();
    }

    private void acknowledge(RecordId id) {
        stringRedisTemplate.opsForStream().acknowledge(STREAM_KEY, GROUP, id);
    }

    private String retryKey(RecordId id) {
        return "appointment:retry:" + id.getValue();
    }

    private String value(Map<Object, Object> fields, String key) {
        Object value = fields.get(key);
        return value == null ? null : value.toString();
    }

    private String rootMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null) root = root.getCause();
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String stockKey(Long scheduleId) {
        return "schedule:stock:" + scheduleId;
    }

    private String bookedKey(Long scheduleId) {
        return "schedule:booked:v2:" + scheduleId;
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String rootMessage(Exception e) {
        Throwable root = e;
        while (root.getCause() != null) root = root.getCause();
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }

    @Override
    public Result grabExpertSchedule(Long scheduleId) {
        if (scheduleId == null || scheduleService.getById(scheduleId) == null) {
            appointmentMetrics.recordAdmission("schedule_missing");
            return Result.fail("排班不存在");
        }
        if (UserHolder.getUser() == null) {
            appointmentMetrics.recordAdmission("unauthenticated");
            return Result.fail("请先登录");
        }
        Long patientId = UserHolder.getUser().getId();
        long appointmentId = redisIdWorker.nextId("appointment");
        Long result = stringRedisTemplate.execute(GRAB_SCHEDULE_SCRIPT, Collections.emptyList(),
                scheduleId.toString(), patientId.toString(), String.valueOf(appointmentId));
        if (result == null || result != 0L) {
            appointmentMetrics.recordAdmission(result == null ? "redis_error" : result == 1L ? "sold_out" : "duplicate");
            return Result.fail(result != null && result == 1L ? "号源不足" : "不能重复挂号");
        }
        appointmentMetrics.recordAdmission("accepted");
        return Result.ok(appointmentId);
    }

    @Override
    public Result queryMyAppointments() {
        Long patientId = UserHolder.getUser().getId();
        return Result.ok(query().eq("patient_id", patientId).orderByDesc("create_time").list());
    }

    @Override
    public Result queryAppointment(Long id) {
        if (id == null || UserHolder.getUser() == null) return Result.fail("预约不存在");
        Appointment appointment = query().eq("id", id)
                .eq("patient_id", UserHolder.getUser().getId()).one();
        return appointment == null ? Result.fail("预约不存在") : Result.ok(appointment);
    }

    @Override
    public Result cancelAppointment(Long id) {
        Appointment appointment = getById(id);
        if (appointment == null || !appointment.getPatientId().equals(UserHolder.getUser().getId())) {
            return Result.fail("预约不存在");
        }
        if (appointment.getStatus() != null && appointment.getStatus() >= 3) {
            return Result.fail("当前预约不可取消");
        }
        appointment.setStatus(5);
        appointment.setCancelTime(java.time.LocalDateTime.now());
        if (!updateById(appointment)) return Result.fail("取消预约失败");
        scheduleService.update().setSql("available_count = available_count + 1")
                .eq("id", appointment.getScheduleId()).update();
        compensateSchedule(appointment);
        return Result.ok();
    }
}
