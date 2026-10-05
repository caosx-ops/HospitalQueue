package com.hmdp.service.impl;

import com.hmdp.entity.Appointment;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Service
public class AppointmentOrderPersistenceService {

    @Resource
    private AppointmentOrderTransactionService transactionService;

    @Resource
    private RedissonClient redissonClient;

    public void persist(Appointment appointment) {
        RLock lock = redissonClient.getLock("lock:appointment:" + appointment.getPatientId());
        boolean acquired;
        try {
            acquired = lock.tryLock(0, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RetryableAppointmentException("等待预约锁时线程被中断", e);
        }
        if (!acquired) {
            throw new RetryableAppointmentException("患者预约正在处理中");
        }

        try {
            // This call crosses a Spring proxy: the transaction commits before the distributed lock is released.
            transactionService.persistUnderLock(appointment);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    public java.util.List<Appointment> listActiveAppointments() {
        return transactionService.listActiveAppointments();
    }

    public static class RetryableAppointmentException extends RuntimeException {
        public RetryableAppointmentException(String message) { super(message); }
        public RetryableAppointmentException(String message, Throwable cause) { super(message, cause); }
    }

    public static class PermanentAppointmentException extends RuntimeException {
        public PermanentAppointmentException(String message) { super(message); }
    }
}
