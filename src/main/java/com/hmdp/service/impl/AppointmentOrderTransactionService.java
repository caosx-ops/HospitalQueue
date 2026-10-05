package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.entity.Appointment;
import com.hmdp.entity.Schedule;
import com.hmdp.mapper.AppointmentMapper;
import com.hmdp.service.IScheduleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

import static com.hmdp.service.impl.AppointmentOrderPersistenceService.PermanentAppointmentException;
import static com.hmdp.service.impl.AppointmentOrderPersistenceService.RetryableAppointmentException;

@Service
public class AppointmentOrderTransactionService {

    @Resource
    private AppointmentMapper appointmentMapper;

    @Resource
    private IScheduleService scheduleService;

    @Transactional
    public void persistUnderLock(Appointment appointment) {
        if (appointmentMapper.selectById(appointment.getId()) != null) {
            return;
        }
        if (appointmentMapper.selectCount(new QueryWrapper<Appointment>()
                .eq("patient_id", appointment.getPatientId())
                .eq("schedule_id", appointment.getScheduleId())
                .ne("status", 5)) > 0) {
            throw new PermanentAppointmentException("患者已预约该排班");
        }

        Schedule schedule = scheduleService.getById(appointment.getScheduleId());
        if (schedule == null) {
            throw new PermanentAppointmentException("排班不存在");
        }
        if (!scheduleService.decrementAvailableCount(appointment.getScheduleId())) {
            throw new PermanentAppointmentException("数据库号源不足或状态不一致");
        }

        appointment.setDoctorId(schedule.getDoctorId());
        appointment.setStatus(2);
        if (appointmentMapper.insert(appointment) != 1) {
            throw new RetryableAppointmentException("预约记录写入失败");
        }
    }

    @Transactional(readOnly = true)
    public List<Appointment> listActiveAppointments() {
        return appointmentMapper.selectList(new QueryWrapper<Appointment>().ne("status", 5));
    }
}
