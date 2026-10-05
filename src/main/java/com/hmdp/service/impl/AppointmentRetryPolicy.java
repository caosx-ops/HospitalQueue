package com.hmdp.service.impl;

public class AppointmentRetryPolicy {

    private final int maxRetries;
    private final long initialDelayMillis;
    private final long maxDelayMillis;

    public AppointmentRetryPolicy(int maxRetries, long initialDelayMillis, long maxDelayMillis) {
        if (maxRetries < 1 || initialDelayMillis < 1 || maxDelayMillis < initialDelayMillis) {
            throw new IllegalArgumentException("预约重试策略参数无效");
        }
        this.maxRetries = maxRetries;
        this.initialDelayMillis = initialDelayMillis;
        this.maxDelayMillis = maxDelayMillis;
    }

    public boolean exhausted(int failedAttempts) {
        return failedAttempts >= maxRetries;
    }

    public long delayAfterFailure(int failedAttempts) {
        if (failedAttempts < 1) {
            throw new IllegalArgumentException("失败次数必须从 1 开始");
        }
        long multiplier = 1L << Math.min(failedAttempts - 1, 30);
        if (initialDelayMillis > maxDelayMillis / multiplier) {
            return maxDelayMillis;
        }
        return Math.min(maxDelayMillis, initialDelayMillis * multiplier);
    }

    public int getMaxRetries() {
        return maxRetries;
    }
}
