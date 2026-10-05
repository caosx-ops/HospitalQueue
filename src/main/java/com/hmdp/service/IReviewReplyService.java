package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.ReviewReply;

public interface IReviewReplyService extends IService<ReviewReply> {
    Result addReply(ReviewReply reply);
    Result listByReview(Long reviewId);
}
