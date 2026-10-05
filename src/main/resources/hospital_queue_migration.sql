-- ============================
-- 医院智能排队叫号系统数据库
-- 基于hmdp数据库改造
-- ============================

-- 1. 创建新数据库
CREATE DATABASE IF NOT EXISTS hospital_queue DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE hospital_queue;

-- 2. 用户表改为患者表
DROP TABLE IF EXISTS `tb_patient`;
CREATE TABLE `tb_patient` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `phone` varchar(11) NOT NULL COMMENT '手机号码',
  `password` varchar(128) DEFAULT '' COMMENT '密码，加密存储',
  `nick_name` varchar(32) DEFAULT '' COMMENT '昵称，默认是随机字符',
  `icon` varchar(255) DEFAULT '' COMMENT '用户头像',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `real_name` varchar(32) DEFAULT NULL COMMENT '真实姓名',
  `id_card` varchar(18) DEFAULT NULL COMMENT '身份证号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uniqe_user_phone` (`phone`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=COMPACT COMMENT='患者表';

-- 3. 商户类型表改为科室类型表
DROP TABLE IF EXISTS `tb_department`;
CREATE TABLE `tb_department` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(32) NOT NULL COMMENT '科室名称',
  `icon` varchar(255) DEFAULT NULL COMMENT '图标',
  `sort` int(3) DEFAULT NULL COMMENT '顺序',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=COMPACT COMMENT='科室类型表';

-- 4. 商户表改为医生表
DROP TABLE IF EXISTS `tb_doctor`;
CREATE TABLE `tb_doctor` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(255) NOT NULL COMMENT '医生姓名',
  `department_id` bigint(20) NOT NULL COMMENT '科室id',
  `photo` varchar(2048) DEFAULT NULL COMMENT '医生照片',
  `room_number` varchar(255) DEFAULT NULL COMMENT '诊室号',
  `hospital_name` varchar(255) NOT NULL COMMENT '医院名称',
  `x` double NOT NULL COMMENT '经度',
  `y` double NOT NULL COMMENT '纬度',
  `consultation_fee` bigint(10) DEFAULT NULL COMMENT '挂号费（分）',
  `patient_count` int(10) DEFAULT '0' COMMENT '累计接诊患者数',
  `review_count` int(10) DEFAULT '0' COMMENT '评价数量',
  `rating` int(2) DEFAULT '5' COMMENT '评分（1-5星）',
  `work_hours` varchar(255) DEFAULT NULL COMMENT '出诊时间',
  `title` varchar(50) DEFAULT NULL COMMENT '职称（主任医师/副主任）',
  `speciality` varchar(500) DEFAULT NULL COMMENT '擅长领域',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_department_id` (`department_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=COMPACT COMMENT='医生信息表';

-- 5. 优惠券表改为排班表
DROP TABLE IF EXISTS `tb_schedule`;
CREATE TABLE `tb_schedule` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `doctor_id` bigint(20) unsigned NOT NULL COMMENT '医生id',
  `title` varchar(255) NOT NULL COMMENT '排班标题（如：上午门诊）',
  `sub_title` varchar(255) DEFAULT NULL COMMENT '副标题',
  `rules` varchar(1024) DEFAULT NULL COMMENT '使用规则',
  `pay_value` bigint(10) unsigned NOT NULL COMMENT '挂号费（分）',
  `actual_value` bigint(10) NOT NULL COMMENT '实际价值（分）',
  `type` tinyint(1) unsigned NOT NULL DEFAULT '0' COMMENT '0-普通号；1-专家号',
  `status` tinyint(1) unsigned NOT NULL DEFAULT '1' COMMENT '1-上架；2-下架；3-过期',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `stock` int(8) NOT NULL COMMENT '号源数量',
  `begin_time` timestamp NULL DEFAULT NULL COMMENT '生效时间',
  `end_time` timestamp NULL DEFAULT NULL COMMENT '失效时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_doctor_id` (`doctor_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=COMPACT COMMENT='医生排班表';

-- 6. 订单表改为挂号表
DROP TABLE IF EXISTS `tb_appointment`;
CREATE TABLE `tb_appointment` (
  `id` bigint(20) NOT NULL COMMENT '主键',
  `patient_id` bigint(20) unsigned NOT NULL COMMENT '患者id',
  `schedule_id` bigint(20) unsigned NOT NULL COMMENT '排班id',
  `doctor_id` bigint(20) unsigned NOT NULL COMMENT '医生id',
  `appointment_number` varchar(20) DEFAULT NULL COMMENT '就诊号（如：A001）',
  `queue_number` int(5) DEFAULT NULL COMMENT '排队序号',
  `pay_type` tinyint(1) unsigned NOT NULL DEFAULT '1' COMMENT '支付方式 1：余额支付；2：支付宝；3：微信',
  `status` tinyint(1) unsigned NOT NULL DEFAULT '1' COMMENT '状态：1-待支付；2-待就诊；3-就诊中；4-已完成；5-已取消',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '挂号时间',
  `pay_time` timestamp NULL DEFAULT NULL COMMENT '支付时间',
  `checkin_time` timestamp NULL DEFAULT NULL COMMENT '签到时间',
  `call_time` timestamp NULL DEFAULT NULL COMMENT '叫号时间',
  `finish_time` timestamp NULL DEFAULT NULL COMMENT '就诊完成时间',
  `cancel_time` timestamp NULL DEFAULT NULL COMMENT '取消时间',
  `estimated_wait_time` int(5) DEFAULT NULL COMMENT '预估等待时间（分钟）',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_patient_id` (`patient_id`) USING BTREE,
  KEY `idx_schedule_id` (`schedule_id`) USING BTREE,
  KEY `idx_doctor_id` (`doctor_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=COMPACT COMMENT='预约挂号表';

-- 7. 探店笔记表改为评价表
DROP TABLE IF EXISTS `tb_review`;
CREATE TABLE `tb_review` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `doctor_id` bigint(20) NOT NULL COMMENT '医生id',
  `user_id` bigint(20) unsigned NOT NULL COMMENT '用户id',
  `title` varchar(255) DEFAULT NULL COMMENT '标题',
  `images` varchar(2048) NOT NULL COMMENT '图片，最多9张，逗号隔开',
  `content` varchar(2048) NOT NULL COMMENT '评价内容',
  `liked` int(8) unsigned DEFAULT '0' COMMENT '点赞数量',
  `comments` int(8) unsigned DEFAULT NULL COMMENT '评论数量',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_user_id` (`user_id`) USING BTREE,
  KEY `idx_doctor_id` (`doctor_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=COMPACT COMMENT='就医评价表';

-- 8. 关注表改为收藏表
DROP TABLE IF EXISTS `tb_favorite`;
CREATE TABLE `tb_favorite` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint(20) unsigned NOT NULL COMMENT '用户id',
  `follow_user_id` bigint(20) unsigned NOT NULL COMMENT '关注的医生id',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_user_id` (`user_id`) USING BTREE,
  KEY `idx_follow_user_id` (`follow_user_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=COMPACT COMMENT='收藏医生表';

-- 9. 评论表保持不变，只改表名
DROP TABLE IF EXISTS `tb_review_reply`;
CREATE TABLE `tb_review_reply` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint(20) unsigned NOT NULL COMMENT '用户id',
  `review_id` bigint(20) unsigned NOT NULL COMMENT '评价id',
  `parent_id` bigint(20) unsigned NOT NULL COMMENT '关联的1级评论id，如果是一级评论，则值为0',
  `answer_id` bigint(20) unsigned NOT NULL COMMENT '回复的评论id',
  `content` varchar(255) NOT NULL COMMENT '回复的内容',
  `liked` int(8) unsigned DEFAULT NULL COMMENT '点赞数',
  `status` tinyint(1) DEFAULT NULL COMMENT '状态，0：正常，1：被举报，2：禁止查看',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_user_id` (`user_id`) USING BTREE,
  KEY `idx_review_id` (`review_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=COMPACT COMMENT='评价回复表';

-- 10. 插入测试数据

-- 插入科室数据
INSERT INTO `tb_department` VALUES
(1, '内科', '/imgs/department/neike.png', 1, NOW(), NOW()),
(2, '外科', '/imgs/department/waike.png', 2, NOW(), NOW()),
(3, '儿科', '/imgs/department/erke.png', 3, NOW(), NOW()),
(4, '妇科', '/imgs/department/fuke.png', 4, NOW(), NOW()),
(5, '骨科', '/imgs/department/guke.png', 5, NOW(), NOW()),
(6, '眼科', '/imgs/department/yanke.png', 6, NOW(), NOW()),
(7, '耳鼻喉科', '/imgs/department/erbihou.png', 7, NOW(), NOW()),
(8, '口腔科', '/imgs/department/kouqiang.png', 8, NOW(), NOW()),
(9, '皮肤科', '/imgs/department/pifu.png', 9, NOW(), NOW()),
(10, '中医科', '/imgs/department/zhongyi.png', 10, NOW(), NOW());

-- 插入患者测试数据
INSERT INTO `tb_patient` (id, phone, password, nick_name, icon, create_time, update_time, real_name, id_card) VALUES
(1, '13800138001', '', '张三', '', NOW(), NOW(), '张三', '110101199001011234'),
(2, '13800138002', '', '李四', '', NOW(), NOW(), '李四', '110101199002021234'),
(3, '13800138003', '', '王五', '', NOW(), NOW(), '王五', '110101199003031234'),
(4, '13800138004', '', '赵六', '', NOW(), NOW(), '赵六', '110101199004041234'),
(5, '13800138005', '', '孙七', '', NOW(), NOW(), '孙七', '110101199005051234');

-- 插入医生测试数据
INSERT INTO `tb_doctor` (id, name, department_id, photo, room_number, hospital_name, x, y, consultation_fee, patient_count, review_count, rating, work_hours, title, speciality) VALUES
(1, '王医生', 1, '', '101', '北京协和医院', 116.397128, 39.916527, 50000, 100, 50, 5, '周一至周五 8:00-17:00', '主任医师', '心血管内科'),
(2, '李医生', 1, '', '102', '北京协和医院', 116.397128, 39.916527, 30000, 80, 40, 4, '周一至周五 8:00-17:00', '主治医师', '消化内科'),
(3, '张医生', 2, '', '201', '北京协和医院', 116.397128, 39.916527, 80000, 150, 80, 5, '周一至周五 8:00-17:00', '主任医师', '普通外科'),
(4, '刘医生', 2, '', '202', '北京协和医院', 116.397128, 39.916527, 40000, 90, 45, 4, '周一至周五 8:00-17:00', '副主任医师', '骨科'),
(5, '陈医生', 3, '', '301', '北京儿童医院', 116.347128, 39.936527, 60000, 200, 100, 5, '周一至周日 8:00-20:00', '主任医师', '儿童呼吸科'),
(6, '赵医生', 3, '', '302', '北京儿童医院', 116.347128, 39.936527, 35000, 120, 60, 4, '周一至周日 8:00-20:00', '主治医师', '儿童消化科'),
(7, '吴医生', 4, '', '401', '北京妇产医院', 116.417128, 39.926527, 70000, 180, 90, 5, '周一至周六 8:00-17:00', '主任医师', '妇科肿瘤'),
(8, '周医生', 5, '', '501', '积水潭医院', 116.367128, 39.946527, 90000, 160, 85, 5, '周一至周五 8:00-17:00', '主任医师', '脊柱外科'),
(9, '郑医生', 6, '', '601', '同仁医院', 116.407128, 39.906527, 55000, 110, 55, 5, '周一至周五 8:00-17:00', '副主任医师', '眼科'),
(10, '孙医生', 7, '', '701', '北京中医院', 116.387128, 39.956527, 45000, 95, 48, 4, '周一至周五 8:00-17:00', '主任医师', '耳鼻喉科');

-- 插入排班测试数据
INSERT INTO `tb_schedule` (id, doctor_id, title, sub_title, rules, pay_value, actual_value, type, status, create_time, update_time, stock, begin_time, end_time) VALUES
(1, 1, '王医生上午门诊', '专家号', '需提前预约', 50000, 50000, 1, 1, NOW(), NOW(), 20, '2024-01-01 08:00:00', '2024-12-31 23:59:59'),
(2, 2, '李医生上午门诊', '普通号', '当天可挂', 30000, 30000, 0, 1, NOW(), NOW(), 30, '2024-01-01 08:00:00', '2024-12-31 23:59:59'),
(3, 3, '张医生上午门诊', '专家号', '需提前预约', 80000, 80000, 1, 1, NOW(), NOW(), 15, '2024-01-01 08:00:00', '2024-12-31 23:59:59'),
(4, 5, '陈医生上午门诊', '专家号', '需提前预约', 60000, 60000, 1, 1, NOW(), NOW(), 25, '2024-01-01 08:00:00', '2024-12-31 23:59:59');

-- 插入评价测试数据
INSERT INTO `tb_review` (id, doctor_id, user_id, title, images, content, liked, comments, create_time, update_time) VALUES
(1, 1, 1, '王医生医术精湛', '', '王医生非常专业，诊断准确，态度和蔼，强烈推荐！', 10, 2, NOW(), NOW()),
(2, 3, 2, '张医生手术很成功', '', '张医生的手术做得很好，恢复得也很快，感谢！', 8, 1, NOW(), NOW()),
(3, 5, 3, '陈医生对孩子很有耐心', '', '陈医生对小朋友特别有耐心，孩子不怕看病了。', 15, 3, NOW(), NOW());

-- 插入收藏测试数据
INSERT INTO `tb_favorite` (id, user_id, follow_user_id, create_time) VALUES
(1, 1, 1, NOW()),
(2, 1, 3, NOW()),
(3, 2, 1, NOW()),
(4, 2, 5, NOW()),
(5, 3, 5, NOW());

-- 完成
SELECT '数据库创建完成！' AS message;
