package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.entity.Appointment;
import com.hmdp.service.IAppointmentService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 * 预约挂号表 前端控制器
 * </p>
 *
 * @author hmdp
 * @since 2024-10-03
 */
@RestController
@RequestMapping("/appointment")
public class AppointmentController {

    @Resource
    private IAppointmentService appointmentService;

    /**
     * 抢专家号
     * @param scheduleId 排班id
     * @return 挂号订单id
     */
    @PostMapping("/grab/{id}")
    public Result grabExpertSchedule(@PathVariable("id") Long scheduleId) {
        return appointmentService.grabExpertSchedule(scheduleId);
    }

    @PostMapping("/seckill/{id}")
    public Result seckill(@PathVariable("id") Long scheduleId) {
        return appointmentService.grabExpertSchedule(scheduleId);
    }

    @GetMapping("/{id}")
    public Result query(@PathVariable Long id) {
        return appointmentService.queryAppointment(id);
    }

    @GetMapping("/my")
    public Result my() { return appointmentService.queryMyAppointments(); }

    @DeleteMapping("/{id}")
    public Result cancel(@PathVariable Long id) { return appointmentService.cancelAppointment(id); }
}
