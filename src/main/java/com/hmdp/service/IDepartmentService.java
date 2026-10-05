package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Department;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 * 科室类型表 服务类
 * </p>
 *
 * @author hmdp
 * @since 2024-10-03
 */
public interface IDepartmentService extends IService<Department> {

    Result queryDepartmentList();

    Result queryById(Long id);
}
