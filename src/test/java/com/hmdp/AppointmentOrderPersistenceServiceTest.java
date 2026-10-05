package com.hmdp;

import com.hmdp.entity.Appointment;
import com.hmdp.service.impl.AppointmentOrderPersistenceService;
import com.hmdp.service.impl.AppointmentOrderTransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class AppointmentOrderPersistenceServiceTest {

    private final AppointmentOrderTransactionService transaction = mock(AppointmentOrderTransactionService.class);
    private final RedissonClient redisson = mock(RedissonClient.class);
    private final RLock lock = mock(RLock.class);
    private final AppointmentOrderPersistenceService service = new AppointmentOrderPersistenceService();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "transactionService", transaction);
        ReflectionTestUtils.setField(service, "redissonClient", redisson);
        when(redisson.getLock("lock:appointment:11")).thenReturn(lock);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
    }

    @Test
    void transactionProxyReturnsBeforeDistributedLockIsReleased() throws Exception {
        when(lock.tryLock(0, TimeUnit.SECONDS)).thenReturn(true);
        Appointment appointment = appointment();

        service.persist(appointment);

        InOrder order = inOrder(transaction, lock);
        order.verify(transaction).persistUnderLock(appointment);
        order.verify(lock).isHeldByCurrentThread();
        order.verify(lock).unlock();
    }

    @Test
    void lockContentionLeavesMessageRetryableWithoutRunningTransaction() throws Exception {
        when(lock.tryLock(0, TimeUnit.SECONDS)).thenReturn(false);

        assertThrows(AppointmentOrderPersistenceService.RetryableAppointmentException.class,
                () -> service.persist(appointment()));

        verifyNoInteractions(transaction);
        verify(lock, never()).unlock();
    }

    @Test
    void transactionFailureStillReleasesLockForStreamRetry() throws Exception {
        when(lock.tryLock(0, TimeUnit.SECONDS)).thenReturn(true);
        doThrow(new RuntimeException("database unavailable")).when(transaction).persistUnderLock(any());

        assertThrows(RuntimeException.class, () -> service.persist(appointment()));

        verify(lock).unlock();
    }

    private Appointment appointment() {
        return new Appointment().setId(99L).setPatientId(11L).setScheduleId(7L);
    }
}
