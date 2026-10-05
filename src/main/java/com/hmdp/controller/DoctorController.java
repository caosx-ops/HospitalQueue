package com.hmdp.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Doctor;
import com.hmdp.service.IDoctorService;
import com.hmdp.utils.SystemConstants;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import static com.hmdp.utils.RedisConstants.DOCTOR_GEO_KEY;

/**
 * <p>
 * 医生信息表 前端控制器
 * </p>
 *
 * @author hmdp
 * @since 2024-10-03
 */
@RestController
@RequestMapping("/doctor")
public class DoctorController {

    @Resource
    public IDoctorService doctorService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 根据id查询医生信息
     * @param id 医生id
     * @return 医生详情数据
     */
    @GetMapping("/{id}")
    public Result queryDoctorById(@PathVariable("id") Long id) {
        return doctorService.queryById(id);
    }

    /**
     * 新增医生信息
     * @param doctor 医生数据
     * @return 医生id
     */
    @PostMapping
    public Result saveDoctor(@RequestBody Doctor doctor) {
        if (doctor.getName() == null || doctor.getDepartmentId() == null
                || doctor.getHospitalName() == null || doctor.getX() == null || doctor.getY() == null) {
            return Result.fail("医生姓名、科室、医院和坐标不能为空");
        }
        // 写入数据库
        if (!doctorService.save(doctor)) return Result.fail("新增医生失败");
        stringRedisTemplate.opsForGeo().add(DOCTOR_GEO_KEY + doctor.getDepartmentId(),
                new org.springframework.data.geo.Point(doctor.getX(), doctor.getY()), doctor.getId().toString());
        // 返回医生id
        return Result.ok(doctor.getId());
    }

    /**
     * 更新医生信息
     * @param doctor 医生数据
     * @return 无
     */
    @PutMapping
    public Result updateDoctor(@RequestBody Doctor doctor) {
        // 写入数据库
        return doctorService.update(doctor);
    }

    /**
     * 根据科室类型分页查询医生信息
     * @param departmentId 科室类型
     * @param current 页码
     * @param x 经度
     * @param y 纬度
     * @return 医生列表
     */
    @GetMapping("/of/department")
    public Result queryDoctorByDepartment(
            @RequestParam("departmentId") Integer departmentId,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "x", required = false) Double x,
            @RequestParam(value = "y", required = false) Double y
    ) {
        return doctorService.queryDoctorByDepartment(departmentId, current, x, y);
    }

    /**
     * 根据医生名称关键字分页查询医生信息
     * @param name 医生名称关键字
     * @param current 页码
     * @return 医生列表
     */
    @GetMapping("/of/name")
    public Result queryDoctorByName(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        // 根据名称分页查询
        Page<Doctor> page = doctorService.query()
                .like(StrUtil.isNotBlank(name), "name", name)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 返回数据
        return Result.ok(page.getRecords());
    }

    /**
     * 查询医生候诊人数
     * @param id 医生id
     * @return 候诊人数
     */
    @GetMapping("/{id}/waiting")
    public Result queryWaitingCount(@PathVariable("id") Long id) {
        Integer count = doctorService.getWaitingCount(id);
        return Result.ok(count);
    }
}
