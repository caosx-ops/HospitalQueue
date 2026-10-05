package com.hmdp;

import com.hmdp.entity.Appointment;
import com.hmdp.entity.Schedule;
import com.hmdp.mapper.AppointmentMapper;
import com.hmdp.service.IScheduleService;
import com.hmdp.service.impl.AppointmentOrderTransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AppointmentOrderTransactionServiceTest {

    private final AppointmentMapper mapper = mock(AppointmentMapper.class);
    private final IScheduleService schedules = mock(IScheduleService.class);
    private final AppointmentOrderTransactionService service = new AppointmentOrderTransactionService();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "appointmentMapper", mapper);
        ReflectionTestUtils.setField(service, "scheduleService", schedules);
    }

    @Test
    void redeliveredAppointmentIdIsIdempotent() {
        Appointment existing = appointment();
        when(mapper.selectById(existing.getId())).thenReturn(existing);

        service.persistUnderLock(existing);

        verify(mapper, never()).insert(any(Appointment.class));
        verifyNoInteractions(schedules);
    }

    @Test
    void existingActivePatientScheduleIsRejectedPermanently() {
        when(mapper.selectCount(any())).thenReturn(1);

        assertThrows(RuntimeException.class, () -> service.persistUnderLock(appointment()));

        verify(schedules, never()).decrementAvailableCount(anyLong());
    }

    @Test
    void successfulPersistenceDecrementsStockAndInsertsOnce() {
        Appointment appointment = appointment();
        when(mapper.selectCount(any())).thenReturn(0);
        when(schedules.getById(7L)).thenReturn(new Schedule().setId(7L).setDoctorId(3L));
        when(schedules.decrementAvailableCount(7L)).thenReturn(true);
        when(mapper.insert(appointment)).thenReturn(1);

        service.persistUnderLock(appointment);

        assertEquals(3L, appointment.getDoctorId());
        assertEquals(2, appointment.getStatus());
        verify(schedules).decrementAvailableCount(7L);
        verify(mapper).insert(appointment);
    }

    @Test
    void databaseStockFailureDoesNotInsertOrder() {
        when(mapper.selectCount(any())).thenReturn(0);
        when(schedules.getById(7L)).thenReturn(new Schedule().setId(7L).setDoctorId(3L));
        when(schedules.decrementAvailableCount(7L)).thenReturn(false);

        assertThrows(RuntimeException.class, () -> service.persistUnderLock(appointment()));

        verify(mapper, never()).insert(any(Appointment.class));
    }

    private Appointment appointment() {
        return new Appointment().setId(99L).setPatientId(11L).setScheduleId(7L);
    }
}
