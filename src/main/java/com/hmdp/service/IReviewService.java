package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Review;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 就医评价表 服务类
 * </p>
 *
 * @author hmdp
 * @since 2024-10-03
 */
public interface IReviewService extends IService<Review> {

    Result queryHotReview(Integer current);

    Result queryReviewById(Long id);

    Result likeReview(Long id);

    Result queryReviewLikes(Long id);

    Result saveReview(Review review);

    Result queryReviewOfFavorite(Long max, Integer offset);

    Result queryReviewByDoctor(Long doctorId, Integer current);
}
