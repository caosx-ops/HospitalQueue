package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.entity.Schedule;
import com.hmdp.service.IScheduleService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 * 医生排班表 前端控制器
 * </p>
 *
 * @author hmdp
 * @since 2024-10-03
 */
@RestController
@RequestMapping("/schedule")
public class ScheduleController {

    @Resource
    private IScheduleService scheduleService;

    /**
     * 添加排班
     * @param schedule 排班信息
     * @return 排班id
     */
    @PostMapping
    public Result addSchedule(@RequestBody Schedule schedule) {
        return scheduleService.addSchedule(schedule);
    }

    @GetMapping("/doctor/{doctorId}")
    public Result queryByDoctor(@PathVariable Long doctorId) {
        return scheduleService.queryByDoctor(doctorId);
    }

    @PutMapping
    public Result updateSchedule(@RequestBody Schedule schedule) {
        if (schedule.getId() == null) return Result.fail("排班id不能为空");
        if (!scheduleService.updateById(schedule)) return Result.fail("排班不存在");
        return Result.ok();
    }
}
