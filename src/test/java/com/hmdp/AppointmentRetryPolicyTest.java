package com.hmdp;

import com.hmdp.service.impl.AppointmentRetryPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AppointmentRetryPolicyTest {

    private final AppointmentRetryPolicy policy = new AppointmentRetryPolicy(5, 100L, 2000L);

    @Test
    void retriesUseBoundedExponentialBackoffAndHaveAnExplicitLimit() {
        assertEquals(100L, policy.delayAfterFailure(1));
        assertEquals(200L, policy.delayAfterFailure(2));
        assertEquals(400L, policy.delayAfterFailure(3));
        assertEquals(800L, policy.delayAfterFailure(4));
        assertEquals(1600L, policy.delayAfterFailure(5));
        assertFalse(policy.exhausted(4));
        assertTrue(policy.exhausted(5));
        assertEquals(2000L, policy.delayAfterFailure(8));
    }

    @Test
    void rejectsInvalidRetryCountsAndPolicyConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> policy.delayAfterFailure(0));
        assertThrows(IllegalArgumentException.class, () -> new AppointmentRetryPolicy(0, 100L, 2000L));
    }
}
