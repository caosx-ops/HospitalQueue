package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Schedule;
import com.hmdp.mapper.ScheduleMapper;
import com.hmdp.service.IScheduleService;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 医生排班表 服务实现类
 * </p>
 *
 * @author hmdp
 * @since 2024-10-03
 */
@Service
public class ScheduleServiceImpl extends ServiceImpl<ScheduleMapper, Schedule> implements IScheduleService {

    @Override
    public Result addSchedule(Schedule schedule) {
        if (schedule.getDoctorId() == null || schedule.getScheduleDate() == null
                || schedule.getTotalCount() == null || schedule.getTotalCount() <= 0) {
            return Result.fail("医生、日期和号源数量不能为空");
        }
        if (schedule.getAvailableCount() == null) {
            schedule.setAvailableCount(schedule.getTotalCount());
        }
        if (schedule.getAvailableCount() < 0 || schedule.getAvailableCount() > schedule.getTotalCount()) {
            return Result.fail("剩余号源数量无效");
        }
        save(schedule);
        return Result.ok(schedule.getId());
    }

    @Override
    public Result queryByDoctor(Long doctorId) {
        if (doctorId == null) {
            return Result.fail("医生id不能为空");
        }
        return Result.ok(query().eq("doctor_id", doctorId)
                .orderByAsc("schedule_date").orderByAsc("shift_type").list());
    }

    @Override
    public boolean decrementAvailableCount(Long scheduleId) {
        return update().setSql("available_count = available_count - 1")
                .eq("id", scheduleId).gt("available_count", 0).update();
    }
}
