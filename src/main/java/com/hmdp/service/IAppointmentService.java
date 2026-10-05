package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Appointment;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 预约挂号表 服务类
 * </p>
 *
 * @author hmdp
 * @since 2024-10-03
 */
public interface IAppointmentService extends IService<Appointment> {

    Result grabExpertSchedule(Long scheduleId);

    Result queryMyAppointments();

    Result queryAppointment(Long id);

    Result cancelAppointment(Long id);
}
