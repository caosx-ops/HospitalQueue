package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Department;
import com.hmdp.mapper.DepartmentMapper;
import com.hmdp.service.IDepartmentService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_DEPARTMENT_KEY;

/**
 * <p>
 * 科室类型表 服务实现类
 * </p>
 *
 * @author hmdp
 * @since 2024-10-03
 */
@Service
public class DepartmentServiceImpl extends ServiceImpl<DepartmentMapper, Department> implements IDepartmentService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryDepartmentList() {
        // 查询所有科室列表
        List<Department> departmentList = query().orderByAsc("sort").list();
        return Result.ok(departmentList);
    }

    @Override
    public Result queryById(Long id) {
        Department department = getById(id);
        return department == null ? Result.fail("科室不存在") : Result.ok(department);
    }
}
