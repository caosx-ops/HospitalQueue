package com.hmdp.service.impl;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class AppointmentMetrics {

    @Resource
    private MeterRegistry meterRegistry;

    public void recordAdmission(String outcome) {
        Counter.builder("hospital.appointment.admission")
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();
    }

    public void recordPersisted() {
        meterRegistry.counter("hospital.appointment.persisted").increment();
    }

    public void recordRetry() {
        meterRegistry.counter("hospital.appointment.retry").increment();
    }

    public void recordDeadLetter() {
        meterRegistry.counter("hospital.appointment.dead_letter").increment();
    }

    public void recordCompensation() {
        meterRegistry.counter("hospital.appointment.compensation").increment();
    }
}
