package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.entity.ReviewReply;
import com.hmdp.service.IReviewReplyService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/review/reply")
public class ReviewReplyController {
    @Resource
    private IReviewReplyService replyService;

    @PostMapping
    public Result add(@RequestBody ReviewReply reply) { return replyService.addReply(reply); }

    @GetMapping("/of/review/{reviewId}")
    public Result list(@PathVariable Long reviewId) { return replyService.listByReview(reviewId); }
}
