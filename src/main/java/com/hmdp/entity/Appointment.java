package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 预约挂号表
 * </p>
 *
 * @author hmdp
 * @since 2024-10-03
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_appointment")
public class Appointment implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    /**
     * 患者id
     */
    private Long patientId;

    /**
     * 排班id
     */
    private Long scheduleId;

    /**
     * 医生id
     */
    private Long doctorId;

    /**
     * 就诊号（如：A001）
     */
    private String appointmentNumber;

    /**
     * 排队序号
     */
    private Integer queueNumber;

    /**
     * 支付方式：1-余额支付；2-支付宝；3-微信
     */
    private Integer payType;

    /**
     * 状态：1-待支付；2-待就诊；3-就诊中；4-已完成；5-已取消；6-过号
     */
    private Integer status;

    /**
     * 挂号时间
     */
    private LocalDateTime createTime;

    /**
     * 支付时间
     */
    private LocalDateTime payTime;

    /**
     * 签到时间
     */
    private LocalDateTime checkinTime;

    /**
     * 叫号时间
     */
    private LocalDateTime callTime;

    /**
     * 就诊完成时间
     */
    private LocalDateTime finishTime;

    /**
     * 取消时间
     */
    private LocalDateTime cancelTime;

    /**
     * 预估等待时间（分钟）
     */
    private Integer estimatedWaitTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
