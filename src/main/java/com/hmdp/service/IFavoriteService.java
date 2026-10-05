package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Favorite;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 收藏医生表 服务类
 * </p>
 *
 * @author hmdp
 * @since 2024-10-03
 */
public interface IFavoriteService extends IService<Favorite> {

    Result favorite(Long doctorId, Boolean isFavorite);

    Result isFavorite(Long doctorId);

    Result favoriteCommons(Long id);

    Result queryMyFavorites();
}
