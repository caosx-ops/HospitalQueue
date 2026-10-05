package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.service.IDepartmentService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 * 科室类型表 前端控制器
 * </p>
 *
 * @author hmdp
 * @since 2024-10-03
 */
@RestController
@RequestMapping("/department")
public class DepartmentController {

    @Resource
    private IDepartmentService departmentService;

    /**
     * 查询科室列表
     * @return 科室列表
     */
    @GetMapping("/list")
    public Result queryDepartmentList() {
        return departmentService.queryDepartmentList();
    }

    @GetMapping("/{id}")
    public Result queryById(@PathVariable Long id) {
        return departmentService.queryById(id);
    }
}
