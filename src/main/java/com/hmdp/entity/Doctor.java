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
 * 医生信息表
 * </p>
 *
 * @author hmdp
 * @since 2024-10-03
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_doctor")
public class Doctor implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 医生姓名
     */
    private String name;

    /**
     * 科室id
     */
    private Long departmentId;

    /**
     * 医生照片
     */
    private String photo;

    /**
     * 诊室号
     */
    private String roomNumber;

    /**
     * 医院名称
     */
    private String hospitalName;

    /**
     * 经度
     */
    private Double x;

    /**
     * 纬度
     */
    private Double y;

    /**
     * 挂号费（分）
     */
    private Long consultationFee;

    /**
     * 累计接诊患者数
     */
    private Integer patientCount;

    /**
     * 评价数量
     */
    private Integer reviewCount;

    /**
     * 评分（1-5）
     */
    private Integer rating;

    /**
     * 出诊时间
     */
    private String workHours;

    /**
     * 职称
     */
    private String title;

    /**
     * 擅长领域
     */
    private String speciality;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 距离（不存数据库）
     */
    @TableField(exist = false)
    private Double distance;

    /**
     * 当前候诊人数（不存数据库）
     */
    @TableField(exist = false)
    private Integer waitingCount;
}
