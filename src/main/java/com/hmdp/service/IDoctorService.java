package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Doctor;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 医生信息表 服务类
 * </p>
 *
 * @author hmdp
 * @since 2024-10-03
 */
public interface IDoctorService extends IService<Doctor> {

    Result queryById(Long id);

    Result update(Doctor doctor);

    Result queryDoctorByDepartment(Integer departmentId, Integer current, Double x, Double y);

    Integer getWaitingCount(Long doctorId);
}
