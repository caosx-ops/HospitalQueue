/*
 Hospital Queue Management System Database
 基于 hmdp.sql 改造
 改造日期: 2024-10-03
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 创建数据库
CREATE DATABASE IF NOT EXISTS hospital_queue DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE hospital_queue;

-- ----------------------------
-- Table structure for tb_review (原 tb_blog)
-- 就医评价表
-- ----------------------------
DROP TABLE IF EXISTS `tb_review`;
CREATE TABLE `tb_review` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `doctor_id` bigint(20) NOT NULL COMMENT '医生id',
  `patient_id` bigint(20) UNSIGNED NOT NULL COMMENT '患者id',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '标题',
  `images` varchar(2048) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '就医照片，最多9张，多张以","隔开',
  `content` varchar(2048) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '就医体验描述',
  `liked` int(8) UNSIGNED NULL DEFAULT 0 COMMENT '点赞数量',
  `comments` int(8) UNSIGNED NULL DEFAULT NULL COMMENT '评论数量',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_doctor_id`(`doctor_id`) USING BTREE,
  INDEX `idx_patient_id`(`patient_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 8 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '就医评价表' ROW_FORMAT = Compact;

-- ----------------------------
-- Records of tb_review (示例数据)
-- ----------------------------
INSERT INTO `tb_review` VALUES (1, 1, 1, '张医生医术精湛，态度和蔼', '/imgs/reviews/1.jpg', '张医生非常耐心，详细询问病情，给出了专业的治疗方案。', 15, 5, '2024-09-20 10:30:00', '2024-09-20 10:30:00');
INSERT INTO `tb_review` VALUES (2, 2, 2, '李医生经验丰富', '/imgs/reviews/2.jpg', '李医生一眼就看出了病因，开的药很有效。', 8, 2, '2024-09-21 14:20:00', '2024-09-21 14:20:00');

-- ----------------------------
-- Table structure for tb_review_reply (原 tb_blog_comments)
-- 评价回复表
-- ----------------------------
DROP TABLE IF EXISTS `tb_review_reply`;
CREATE TABLE `tb_review_reply` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `patient_id` bigint(20) UNSIGNED NOT NULL COMMENT '患者id',
  `review_id` bigint(20) UNSIGNED NOT NULL COMMENT '评价id',
  `parent_id` bigint(20) UNSIGNED NOT NULL COMMENT '关联的1级评论id，如果是一级评论，则值为0',
  `answer_id` bigint(20) UNSIGNED NOT NULL COMMENT '回复的评论id',
  `content` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '回复的内容',
  `liked` int(8) UNSIGNED NULL DEFAULT NULL COMMENT '点赞数',
  `status` tinyint(1) UNSIGNED NULL DEFAULT NULL COMMENT '状态，0：正常，1：被举报，2：禁止查看',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_review_id`(`review_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '评价回复表' ROW_FORMAT = Compact;

-- ----------------------------
-- Table structure for tb_favorite (原 tb_follow)
-- 收藏医生表
-- ----------------------------
DROP TABLE IF EXISTS `tb_favorite`;
CREATE TABLE `tb_favorite` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `patient_id` bigint(20) UNSIGNED NOT NULL COMMENT '患者id',
  `doctor_id` bigint(20) UNSIGNED NOT NULL COMMENT '医生id',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_patient_doctor` (`patient_id`, `doctor_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '收藏医生表' ROW_FORMAT = Compact;

-- ----------------------------
-- Table structure for tb_schedule (原 tb_seckill_voucher)
-- 医生排班表（专家号）
-- ----------------------------
DROP TABLE IF EXISTS `tb_schedule`;
CREATE TABLE `tb_schedule` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `doctor_id` bigint(20) UNSIGNED NOT NULL COMMENT '医生id',
  `schedule_date` date NOT NULL COMMENT '排班日期',
  `shift_type` tinyint(1) NOT NULL DEFAULT 1 COMMENT '班次类型：1-上午，2-下午，3-晚上',
  `total_count` int(8) NOT NULL COMMENT '总号源数量',
  `available_count` int(8) NOT NULL COMMENT '剩余号源',
  `begin_time` time NOT NULL DEFAULT '08:00:00' COMMENT '开始时间',
  `end_time` time NOT NULL DEFAULT '12:00:00' COMMENT '结束时间',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_doctor_date_shift` (`doctor_id`, `schedule_date`, `shift_type`) USING BTREE,
  INDEX `idx_schedule_date`(`schedule_date`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '医生排班表' ROW_FORMAT = Compact;

-- ----------------------------
-- Records of tb_schedule (示例数据)
-- ----------------------------
INSERT INTO `tb_schedule` VALUES (1, 1, '2024-10-04', 1, 50, 30, '08:00:00', '12:00:00', NOW(), NOW());
INSERT INTO `tb_schedule` VALUES (2, 1, '2024-10-04', 2, 50, 45, '14:00:00', '18:00:00', NOW(), NOW());
INSERT INTO `tb_schedule` VALUES (3, 2, '2024-10-04', 1, 30, 10, '08:00:00', '12:00:00', NOW(), NOW());

-- ----------------------------
-- Table structure for tb_department (原 tb_shop_type)
-- 科室类型表
-- ----------------------------
DROP TABLE IF EXISTS `tb_department`;
CREATE TABLE `tb_department` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '科室名称',
  `icon` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '图标',
  `sort` int(3) UNSIGNED NULL DEFAULT 0 COMMENT '顺序',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 11 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '科室类型表' ROW_FORMAT = Compact;

-- ----------------------------
-- Records of tb_department
-- ----------------------------
INSERT INTO `tb_department` VALUES (1, '内科', '/imgs/departments/internal.png', 1, NOW(), NOW());
INSERT INTO `tb_department` VALUES (2, '外科', '/imgs/departments/surgery.png', 2, NOW(), NOW());
INSERT INTO `tb_department` VALUES (3, '儿科', '/imgs/departments/pediatrics.png', 3, NOW(), NOW());
INSERT INTO `tb_department` VALUES (4, '妇产科', '/imgs/departments/gynecology.png', 4, NOW(), NOW());
INSERT INTO `tb_department` VALUES (5, '骨科', '/imgs/departments/orthopedics.png', 5, NOW(), NOW());
INSERT INTO `tb_department` VALUES (6, '眼科', '/imgs/departments/ophthalmology.png', 6, NOW(), NOW());
INSERT INTO `tb_department` VALUES (7, '耳鼻喉科', '/imgs/departments/ent.png', 7, NOW(), NOW());
INSERT INTO `tb_department` VALUES (8, '口腔科', '/imgs/departments/dental.png', 8, NOW(), NOW());
INSERT INTO `tb_department` VALUES (9, '皮肤科', '/imgs/departments/dermatology.png', 9, NOW(), NOW());
INSERT INTO `tb_department` VALUES (10, '中医科', '/imgs/departments/tcm.png', 10, NOW(), NOW());

-- ----------------------------
-- Table structure for tb_doctor (原 tb_shop)
-- 医生信息表
-- ----------------------------
DROP TABLE IF EXISTS `tb_doctor`;
CREATE TABLE `tb_doctor` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '医生姓名',
  `department_id` bigint(20) NOT NULL COMMENT '科室id',
  `photo` varchar(2048) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '医生照片',
  `room_number` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '诊室号',
  `hospital_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '医院名称',
  `x` double NOT NULL COMMENT '经度',
  `y` double NOT NULL COMMENT '纬度',
  `consultation_fee` bigint(10) NULL DEFAULT 0 COMMENT '挂号费（分）',
  `patient_count` int(10) UNSIGNED NULL DEFAULT 0 COMMENT '累计接诊患者数',
  `review_count` int(10) UNSIGNED NULL DEFAULT 0 COMMENT '评价数量',
  `rating` int(2) UNSIGNED NULL DEFAULT 5 COMMENT '评分（1-5）',
  `work_hours` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '出诊时间',
  `title` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '职称',
  `speciality` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '擅长领域',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_department_id`(`department_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 20 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '医生信息表' ROW_FORMAT = Compact;

-- ----------------------------
-- Records of tb_doctor (示例数据)
-- ----------------------------
INSERT INTO `tb_doctor` VALUES (1, '张伟', 1, '/imgs/doctors/1.jpg', '201', '市第一人民医院', 114.305392, 30.593099, 1500, 5000, 120, 5, '周一至周五 08:00-12:00', '主任医师', '高血压、糖尿病、心脏病', NOW(), NOW());
INSERT INTO `tb_doctor` VALUES (2, '李娜', 1, '/imgs/doctors/2.jpg', '202', '市第一人民医院', 114.305392, 30.593099, 1000, 3000, 85, 5, '周一至周五 14:00-18:00', '副主任医师', '呼吸系统疾病、肺炎', NOW(), NOW());
INSERT INTO `tb_doctor` VALUES (3, '王强', 2, '/imgs/doctors/3.jpg', '301', '市第一人民医院', 114.305392, 30.593099, 2000, 8000, 200, 5, '周一至周六 08:00-17:00', '主任医师', '普外科手术、阑尾炎、疝气', NOW(), NOW());
INSERT INTO `tb_doctor` VALUES (4, '刘芳', 3, '/imgs/doctors/4.jpg', '401', '市妇幼保健院', 114.298569, 30.584355, 1200, 4500, 150, 5, '周一至周日 08:00-18:00', '主任医师', '儿童常见病、新生儿护理', NOW(), NOW());
INSERT INTO `tb_doctor` VALUES (5, '陈明', 4, '/imgs/doctors/5.jpg', '501', '市妇幼保健院', 114.298569, 30.584355, 1500, 6000, 180, 5, '周一至周六 08:00-17:00', '主任医师', '产科、妇科肿瘤', NOW(), NOW());

-- ----------------------------
-- Table structure for tb_patient (原 tb_user)
-- 患者信息表
-- ----------------------------
DROP TABLE IF EXISTS `tb_patient`;
CREATE TABLE `tb_patient` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `phone` varchar(11) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '手机号码',
  `password` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT '' COMMENT '密码，加密存储',
  `nick_name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '昵称，默认是随机字符',
  `icon` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '头像',
  `real_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '真实姓名',
  `id_card` varchar(18) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '身份证号',
  `gender` tinyint(1) NULL DEFAULT 0 COMMENT '性别：0-未知，1-男，2-女',
  `age` int(3) NULL DEFAULT NULL COMMENT '年龄',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_phone` (`phone`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1010 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '患者信息表' ROW_FORMAT = Compact;

-- ----------------------------
-- Records of tb_patient (示例数据)
-- ----------------------------
INSERT INTO `tb_patient` VALUES (1, '13800138000', '', '张三', '/imgs/avatars/default.png', '张三', '420101199001011234', 1, 34, NOW(), NOW());
INSERT INTO `tb_patient` VALUES (2, '13800138001', '', '李四', '/imgs/avatars/default.png', '李四', '420101199102021234', 2, 33, NOW(), NOW());

-- ----------------------------
-- Table structure for tb_patient_info (原 tb_user_info)
-- 患者详细信息表
-- ----------------------------
DROP TABLE IF EXISTS `tb_patient_info`;
CREATE TABLE `tb_patient_info` (
  `patient_id` bigint(20) UNSIGNED NOT NULL COMMENT '主键，患者id',
  `city` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '城市名称',
  `introduce` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '个人介绍',
  `fans` int(8) UNSIGNED NULL DEFAULT 0 COMMENT '粉丝数量',
  `followee` int(8) UNSIGNED NULL DEFAULT 0 COMMENT '关注的人的数量',
  `gender` tinyint(1) UNSIGNED NULL DEFAULT 0 COMMENT '性别，0：男，1：女',
  `birthday` date NULL DEFAULT NULL COMMENT '生日',
  `credits` int(8) UNSIGNED NULL DEFAULT 0 COMMENT '积分',
  `level` tinyint(1) UNSIGNED NULL DEFAULT 0 COMMENT '会员级别，0~9级',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`patient_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '患者详细信息表' ROW_FORMAT = Compact;

-- ----------------------------
-- Table structure for tb_appointment (原 tb_voucher_order)
-- 预约挂号表
-- ----------------------------
DROP TABLE IF EXISTS `tb_appointment`;
CREATE TABLE `tb_appointment` (
  `id` bigint(20) NOT NULL COMMENT '主键',
  `patient_id` bigint(20) UNSIGNED NOT NULL COMMENT '患者id',
  `schedule_id` bigint(20) UNSIGNED NOT NULL COMMENT '排班id',
  `doctor_id` bigint(20) UNSIGNED NOT NULL COMMENT '医生id',
  `appointment_number` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '就诊号（如：A001）',
  `queue_number` int(5) NULL DEFAULT NULL COMMENT '排队序号',
  `pay_type` tinyint(1) UNSIGNED NOT NULL DEFAULT 1 COMMENT '支付方式：1-余额支付；2-支付宝；3-微信',
  `status` tinyint(1) UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1-待支付；2-待就诊；3-就诊中；4-已完成；5-已取消；6-过号',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '挂号时间',
  `pay_time` timestamp NULL DEFAULT NULL COMMENT '支付时间',
  `checkin_time` timestamp NULL DEFAULT NULL COMMENT '签到时间',
  `call_time` timestamp NULL DEFAULT NULL COMMENT '叫号时间',
  `finish_time` timestamp NULL DEFAULT NULL COMMENT '就诊完成时间',
  `cancel_time` timestamp NULL DEFAULT NULL COMMENT '取消时间',
  `estimated_wait_time` int(5) NULL DEFAULT NULL COMMENT '预估等待时间（分钟）',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_patient_id`(`patient_id`) USING BTREE,
  INDEX `idx_schedule_id`(`schedule_id`) USING BTREE,
  INDEX `idx_doctor_id`(`doctor_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '预约挂号表' ROW_FORMAT = Compact;

SET FOREIGN_KEY_CHECKS = 1;
