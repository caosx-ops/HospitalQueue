package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Schedule;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 医生排班表 服务类
 * </p>
 *
 * @author hmdp
 * @since 2024-10-03
 */
public interface IScheduleService extends IService<Schedule> {

    Result addSchedule(Schedule schedule);

    Result queryByDoctor(Long doctorId);

    boolean decrementAvailableCount(Long scheduleId);
}
