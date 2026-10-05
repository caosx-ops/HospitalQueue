package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Doctor;
import com.hmdp.mapper.DoctorMapper;
import com.hmdp.service.IDoctorService;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.SystemConstants;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 医生信息表 服务实现类
 * </p>
 *
 * @author hmdp
 * @since 2024-10-03
 */
@Service
public class DoctorServiceImpl extends ServiceImpl<DoctorMapper, Doctor> implements IDoctorService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    @PostConstruct
    private void initGeo() {
        try {
            list().forEach(doctor -> stringRedisTemplate.opsForGeo().add(
                    DOCTOR_GEO_KEY + doctor.getDepartmentId(),
                    new org.springframework.data.geo.Point(doctor.getX(), doctor.getY()),
                    doctor.getId().toString()));
        } catch (Exception ignored) {
            // Redis may be temporarily unavailable during startup; normal queries still work.
        }
    }

    @Override
    public Result queryById(Long id) {
        // 空值缓存避免穿透；逻辑过期时返回旧值并异步重建，减少热点缓存失效时的回源压力。
        Doctor doctor = cacheClient
                .queryWithLogicalExpire(CACHE_DOCTOR_KEY, id, Doctor.class, this::getById, CACHE_DOCTOR_TTL, TimeUnit.MINUTES);

        if (doctor == null) {
            return Result.fail("医生不存在！");
        }

        // 查询候诊人数
        doctor.setWaitingCount(getWaitingCount(id));

        return Result.ok(doctor);
    }

    @Override
    @Transactional
    public Result update(Doctor doctor) {
        Long id = doctor.getId();
        if (id == null) {
            return Result.fail("医生id不能为空");
        }
        Doctor old = getById(id);
        // 1.更新数据库
        updateById(doctor);
        // 2.删除缓存
        stringRedisTemplate.delete(CACHE_DOCTOR_KEY + id);
        if (old != null) {
            stringRedisTemplate.opsForGeo().remove(DOCTOR_GEO_KEY + old.getDepartmentId(), id.toString());
            Doctor current = getById(id);
            if (current != null && current.getX() != null && current.getY() != null) {
                stringRedisTemplate.opsForGeo().add(DOCTOR_GEO_KEY + current.getDepartmentId(),
                        new org.springframework.data.geo.Point(current.getX(), current.getY()), id.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryDoctorByDepartment(Integer departmentId, Integer current, Double x, Double y) {
        // 1.判断是否需要根据坐标查询
        if (x == null || y == null) {
            // 不需要坐标查询，按数据库查询
            Page<Doctor> page = query()
                    .eq("department_id", departmentId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }

        // 2.计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

        // 3.查询redis、按照距离排序、分页。结果：doctorId、distance
        String key = DOCTOR_GEO_KEY + departmentId;
        // GEORADIUS is supported by Redis 3.x+; GEOSEARCH requires Redis 6.2.
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .radius(key, new Circle(new org.springframework.data.geo.Point(x, y), new Distance(5000)),
                        RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                                .includeDistance().sortAscending().limit(end));
        // 4.解析出id
        if (results == null) {
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if (list.size() <= from) {
            // 没有下一页了，结束
            return Result.ok(Collections.emptyList());
        }
        // 4.1.截取 from ~ end的部分
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result -> {
            // 4.2.获取医生id
            String doctorIdStr = result.getContent().getName();
            ids.add(Long.valueOf(doctorIdStr));
            // 4.3.获取距离
            Distance distance = result.getDistance();
            distanceMap.put(doctorIdStr, distance);
        });
        // 5.根据id查询Doctor
        String idStr = StrUtil.join(",", ids);
        List<Doctor> doctors = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        for (Doctor doctor : doctors) {
            doctor.setDistance(distanceMap.get(doctor.getId().toString()).getValue());
            // 设置候诊人数
            doctor.setWaitingCount(getWaitingCount(doctor.getId()));
        }
        // 6.返回
        return Result.ok(doctors);
    }

    @Override
    public Integer getWaitingCount(Long doctorId) {
        String key = "queue:doctor:" + doctorId;
        Long count = stringRedisTemplate.opsForZSet().zCard(key);
        return count != null ? count.intValue() : 0;
    }
}
