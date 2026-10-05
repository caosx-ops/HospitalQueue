package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * <p>
 * 医生排班表（专家号）
 * </p>
 *
 * @author hmdp
 * @since 2024-10-03
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_schedule")
public class Schedule implements Serializable {

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
     * 排班日期
     */
    private LocalDate scheduleDate;

    /**
     * 班次类型：1-上午，2-下午，3-晚上
     */
    private Integer shiftType;

    /**
     * 总号源数量
     */
    private Integer totalCount;

    /**
     * 剩余号源
     */
    private Integer availableCount;

    /**
     * 开始时间
     */
    private LocalTime beginTime;

    /**
     * 结束时间
     */
    private LocalTime endTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
