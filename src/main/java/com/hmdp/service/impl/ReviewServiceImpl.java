package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Favorite;
import com.hmdp.entity.Review;
import com.hmdp.entity.User;
import com.hmdp.mapper.ReviewMapper;
import com.hmdp.service.IFavoriteService;
import com.hmdp.service.IReviewService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.FEED_KEY;
import static com.hmdp.utils.RedisConstants.REVIEW_LIKED_KEY;

/**
 * <p>
 * 就医评价表 服务实现类
 * </p>
 *
 * @author hmdp
 * @since 2024-10-03
 */
@Service
public class ReviewServiceImpl extends ServiceImpl<ReviewMapper, Review> implements IReviewService {

    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IFavoriteService favoriteService;

    @Override
    public Result queryHotReview(Integer current) {
        // 根据点赞数查询热门评价
        Page<Review> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Review> records = page.getRecords();
        // 查询用户
        records.forEach(review -> {
            this.queryReviewUser(review);
            this.isReviewLiked(review);
        });
        return Result.ok(records);
    }

    @Override
    public Result queryReviewById(Long id) {
        // 1.查询评价
        Review review = getById(id);
        if (review == null) {
            return Result.fail("评价不存在！");
        }
        // 2.查询评价相关的用户
        queryReviewUser(review);
        // 3.查询评价是否被点赞
        isReviewLiked(review);
        return Result.ok(review);
    }

    private void isReviewLiked(Review review) {
        // 1.获取登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            // 用户未登录，无需查询是否点赞
            return;
        }
        Long userId = user.getId();
        // 2.判断当前登录用户是否已经点赞
        String key = REVIEW_LIKED_KEY + review.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        review.setIsLike(score != null);
    }

    @Override
    public Result likeReview(Long id) {
        // 1.获取登录用户
        Long userId = UserHolder.getUser().getId();
        // 2.判断当前登录用户是否已经点赞
        String key = REVIEW_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if (score == null) {
            // 3.如果未点赞，可以点赞
            // 3.1.数据库点赞数 + 1
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            // 3.2.保存用户到Redis的ZSet集合
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        } else {
            // 4.如果已点赞，取消点赞
            // 4.1.数据库点赞数 -1
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            // 4.2.把用户从Redis的ZSet集合移除
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryReviewLikes(Long id) {
        String key = REVIEW_LIKED_KEY + id;
        // 1.查询top5的点赞用户
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        // 2.解析出其中的用户id
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr = StrUtil.join(",", ids);
        // 3.根据用户id查询用户
        List<UserDTO> userDTOS = userService.query()
                .in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        // 4.返回
        return Result.ok(userDTOS);
    }

    @Override
    public Result saveReview(Review review) {
        // 1.获取登录用户
        UserDTO user = UserHolder.getUser();
        if (review.getDoctorId() == null || StrUtil.isBlank(review.getContent())) {
            return Result.fail("医生和评价内容不能为空");
        }
        // 数据库中的标题和图片字段为非空，接口允许客户端省略时使用空值语义保存。
        if (StrUtil.isBlank(review.getTitle())) {
            review.setTitle("就医评价");
        }
        if (review.getImages() == null) {
            review.setImages("");
        }
        if (review.getLiked() == null) {
            review.setLiked(0);
        }
        if (review.getComments() == null) {
            review.setComments(0);
        }
        review.setPatientId(user.getId());
        // 2.保存评价
        boolean isSuccess = save(review);
        if (!isSuccess) {
            return Result.fail("新增评价失败!");
        }
        // 3.查询评价作者收藏的所有医生的粉丝
        // 将评价推送给收藏该医生的患者
        List<Favorite> fans = favoriteService.query().eq("doctor_id", review.getDoctorId()).list();
        for (Favorite fan : fans) {
            String key = FEED_KEY + fan.getPatientId();
            stringRedisTemplate.opsForZSet().add(key, review.getId().toString(), System.currentTimeMillis());
        }
        // 5.返回id
        return Result.ok(review.getId());
    }

    @Override
    public Result queryReviewOfFavorite(Long max, Integer offset) {
        // 1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        // 2.查询收件箱
        String key = FEED_KEY + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        // 3.非空判断
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok();
        }
        // 4.解析数据：reviewId、minTime、offset
        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0;
        int os = 1;
        for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
            // 4.1.获取id
            ids.add(Long.valueOf(tuple.getValue()));
            // 4.2.获取分数(时间戳）
            long time = tuple.getScore().longValue();
            if (time == minTime) {
                os++;
            } else {
                minTime = time;
                os = 1;
            }
        }

        // 5.根据id查询评价
        String idStr = StrUtil.join(",", ids);
        List<Review> reviews = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();

        for (Review review : reviews) {
            // 5.1.查询评价相关的用户
            queryReviewUser(review);
            // 5.2.查询评价是否被点赞
            isReviewLiked(review);
        }

        // 6.封装并返回
        ScrollResult r = new ScrollResult();
        r.setList(reviews);
        r.setOffset(os);
        r.setMinTime(minTime);

        return Result.ok(r);
    }

    @Override
    public Result queryReviewByDoctor(Long doctorId, Integer current) {
        Page<Review> page = query().eq("doctor_id", doctorId)
                .orderByDesc("create_time")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        page.getRecords().forEach(this::queryReviewUser);
        return Result.ok(page.getRecords(), page.getTotal());
    }

    private void queryReviewUser(Review review) {
        Long patientId = review.getPatientId();
        User user = userService.getById(patientId);
        if (user != null) {
            review.setName(user.getNickName());
            review.setIcon(user.getIcon());
        }
    }
}
