package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.service.IFavoriteService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 * 收藏医生表 前端控制器
 * </p>
 *
 * @author hmdp
 * @since 2024-10-03
 */
@RestController
@RequestMapping("/favorite")
public class FavoriteController {

    @Resource
    private IFavoriteService favoriteService;

    /**
     * 收藏或取消收藏医生
     * @param doctorId 医生id
     * @param isFavorite 是否收藏
     * @return 无
     */
    @PutMapping("/{id}/{isFavorite}")
    public Result favorite(@PathVariable("id") Long doctorId, @PathVariable("isFavorite") Boolean isFavorite) {
        return favoriteService.favorite(doctorId, isFavorite);
    }

    /**
     * 判断是否收藏了医生
     * @param doctorId 医生id
     * @return 是否收藏
     */
    @GetMapping("/or/not/{id}")
    public Result isFavorite(@PathVariable("id") Long doctorId) {
        return favoriteService.isFavorite(doctorId);
    }

    /**
     * 查询共同收藏的医生
     * @param id 用户id
     * @return 医生列表
     */
    @GetMapping("/common/{id}")
    public Result favoriteCommons(@PathVariable("id") Long id) {
        return favoriteService.favoriteCommons(id);
    }

    @GetMapping("/my")
    public Result myFavorites() {
        return favoriteService.queryMyFavorites();
    }
}
