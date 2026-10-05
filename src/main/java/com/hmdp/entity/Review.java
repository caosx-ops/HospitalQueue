package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 就医评价表
 * </p>
 *
 * @author hmdp
 * @since 2024-10-03
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_review")
public class Review implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 医生id
     */
    private Long doctorId;

    /**
     * 患者id
     */
    private Long patientId;

    /**
     * 患者头像（不存数据库）
     */
    @TableField(exist = false)
    private String icon;

    /**
     * 患者姓名（不存数据库）
     */
    @TableField(exist = false)
    private String name;

    /**
     * 是否点赞过了（不存数据库）
     */
    @TableField(exist = false)
    private Boolean isLike;

    /**
     * 标题
     */
    private String title;

    /**
     * 就医照片，最多9张，多张以","隔开
     */
    private String images;

    /**
     * 就医体验描述
     */
    private String content;

    /**
     * 点赞数量
     */
    private Integer liked;

    /**
     * 评论数量
     */
    private Integer comments;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
