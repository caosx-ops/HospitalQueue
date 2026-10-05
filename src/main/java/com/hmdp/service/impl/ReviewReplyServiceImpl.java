package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.ReviewReply;
import com.hmdp.mapper.ReviewReplyMapper;
import com.hmdp.service.IReviewReplyService;
import com.hmdp.service.IReviewService;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class ReviewReplyServiceImpl extends ServiceImpl<ReviewReplyMapper, ReviewReply> implements IReviewReplyService {
    @Resource
    private IReviewService reviewService;

    @Override
    public Result addReply(ReviewReply reply) {
        if (reply.getReviewId() == null || reply.getContent() == null || reply.getContent().trim().isEmpty()) {
            return Result.fail("评价和回复内容不能为空");
        }
        if (reviewService.getById(reply.getReviewId()) == null) return Result.fail("评价不存在");
        reply.setPatientId(UserHolder.getUser().getId());
        if (reply.getParentId() == null) reply.setParentId(0L);
        if (reply.getAnswerId() == null) reply.setAnswerId(0L);
        reply.setStatus(false);
        save(reply);
        reviewService.update().setSql("comments = COALESCE(comments, 0) + 1")
                .eq("id", reply.getReviewId()).update();
        return Result.ok(reply.getId());
    }

    @Override
    public Result listByReview(Long reviewId) {
        return Result.ok(query().eq("review_id", reviewId).eq("status", false)
                .orderByAsc("create_time").list());
    }
}
