package com.hmdp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NormalTest {

    @Test
    void bitmapCountsConsecutiveSignInDaysFromTodayBackwards() {
        long signBits = 0b11101111L;
        int consecutive = 0;
        while ((signBits & 1L) == 1L) {
            consecutive++;
            signBits >>>= 1;
        }
        assertEquals(4, consecutive);
    }
}
