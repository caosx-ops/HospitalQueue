package com.hmdp.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Review;
import com.hmdp.service.IReviewService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 就医评价表 前端控制器
 * </p>
 *
 * @author hmdp
 * @since 2024-10-03
 */
@RestController
@RequestMapping("/review")
public class ReviewController {

    @Resource
    private IReviewService reviewService;

    /**
     * 发布评价
     * @param review 评价内容
     * @return 评价id
     */
    @PostMapping
    public Result saveReview(@RequestBody Review review) {
        return reviewService.saveReview(review);
    }

    /**
     * 点赞评价
     * @param id 评价id
     * @return 无
     */
    @PutMapping("/like/{id}")
    public Result likeReview(@PathVariable("id") Long id) {
        return reviewService.likeReview(id);
    }

    /**
     * 查询我的评价
     * @param current 页码
     * @return 评价列表
     */
    @GetMapping("/of/me")
    public Result queryMyReview(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户查询
        Page<Review> page = reviewService.query()
                .eq("patient_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Review> records = page.getRecords();
        return Result.ok(records);
    }

    /**
     * 查询热门评价
     * @param current 页码
     * @return 评价列表
     */
    @GetMapping("/hot")
    public Result queryHotReview(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return reviewService.queryHotReview(current);
    }

    /**
     * 根据id查询评价
     * @param id 评价id
     * @return 评价详情
     */
    @GetMapping("/{id}")
    public Result queryReviewById(@PathVariable("id") Long id) {
        return reviewService.queryReviewById(id);
    }

    /**
     * 查询评价的点赞用户列表
     * @param id 评价id
     * @return 用户列表
     */
    @GetMapping("/likes/{id}")
    public Result queryReviewLikes(@PathVariable("id") Long id) {
        return reviewService.queryReviewLikes(id);
    }

    /**
     * 根据用户id查询评价
     * @param current 页码
     * @param id 用户id
     * @return 评价列表
     */
    @GetMapping("/of/user")
    public Result queryReviewByUserId(
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam("id") Long id) {
        // 根据用户查询
        Page<Review> page = reviewService.query()
                .eq("patient_id", id).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Review> records = page.getRecords();
        return Result.ok(records);
    }

    @GetMapping("/of/doctor")
    public Result queryReviewByDoctor(
            @RequestParam("doctorId") Long doctorId,
            @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return reviewService.queryReviewByDoctor(doctorId, current);
    }

    /**
     * 查询收藏医生的评价推送
     * @param max 最大时间戳
     * @param offset 偏移量
     * @return 评价列表
     */
    @GetMapping("/of/favorite")
    public Result queryReviewOfFavorite(
            @RequestParam("lastId") Long max,
            @RequestParam(value = "offset", defaultValue = "0") Integer offset) {
        return reviewService.queryReviewOfFavorite(max, offset);
    }
}
