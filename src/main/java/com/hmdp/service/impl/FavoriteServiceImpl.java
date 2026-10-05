package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Doctor;
import com.hmdp.entity.Favorite;
import com.hmdp.mapper.FavoriteMapper;
import com.hmdp.service.IDoctorService;
import com.hmdp.service.IFavoriteService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 收藏医生表 服务实现类
 * </p>
 *
 * @author hmdp
 * @since 2024-10-03
 */
@Service
public class FavoriteServiceImpl extends ServiceImpl<FavoriteMapper, Favorite> implements IFavoriteService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IDoctorService doctorService;

    @PostConstruct
    private void initRedisFavorites() {
        try {
            list().forEach(favorite -> stringRedisTemplate.opsForSet()
                    .add("favorites:" + favorite.getPatientId(), favorite.getDoctorId().toString()));
        } catch (Exception ignored) {
            // Redis may be unavailable during startup; database remains the source of truth.
        }
    }

    @Override
    public Result favorite(Long doctorId, Boolean isFavorite) {
        // 1.获取登录用户
        Long userId = UserHolder.getUser().getId();
        String key = "favorites:" + userId;
        // 1.判断到底是收藏还是取消收藏
        if (isFavorite) {
            if (query().eq("patient_id", userId).eq("doctor_id", doctorId).count() > 0) {
                stringRedisTemplate.opsForSet().add(key, doctorId.toString());
                return Result.ok();
            }
            // 2.收藏，新增数据
            Favorite favorite = new Favorite();
            favorite.setPatientId(userId);
            favorite.setDoctorId(doctorId);
            boolean isSuccess = save(favorite);
            if (isSuccess) {
                // 把收藏医生的id，放入redis的set集合
                stringRedisTemplate.opsForSet().add(key, doctorId.toString());
            }
        } else {
            // 3.取消收藏，删除
            boolean isSuccess = remove(new QueryWrapper<Favorite>()
                    .eq("patient_id", userId).eq("doctor_id", doctorId));
            if (isSuccess) {
                // 把收藏医生的id从Redis集合中移除
                stringRedisTemplate.opsForSet().remove(key, doctorId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result isFavorite(Long doctorId) {
        // 1.获取登录用户
        Long userId = UserHolder.getUser().getId();
        // 2.查询是否收藏
        Integer count = query().eq("patient_id", userId).eq("doctor_id", doctorId).count();
        // 3.判断
        return Result.ok(count > 0);
    }

    @Override
    public Result favoriteCommons(Long id) {
        // 1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        String key = "favorites:" + userId;
        // 2.求交集（共同收藏的医生）
        String key2 = "favorites:" + id;
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key, key2);
        if (intersect == null || intersect.isEmpty()) {
            // 无交集
            return Result.ok(Collections.emptyList());
        }
        // 3.解析id集合
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        // 4.查询医生
        List<Doctor> doctors = doctorService.listByIds(ids);
        return Result.ok(doctors);
    }

    @Override
    public Result queryMyFavorites() {
        Long userId = UserHolder.getUser().getId();
        List<Long> ids = query().eq("patient_id", userId).orderByDesc("create_time")
                .list().stream().map(Favorite::getDoctorId).collect(Collectors.toList());
        return ids.isEmpty() ? Result.ok(Collections.emptyList()) : Result.ok(doctorService.listByIds(ids));
    }
}
