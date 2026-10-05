# 医院智能排队叫号调度系统

## 项目简介

基于 Spring Boot + Redis 的医院预约排队后端 Demo，面向高并发号源预占、异步落库和最终一致性场景。项目没有线上规模数据，文档中的性能数字只引用可复现实测记录。

## 技术栈

- **后端框架**: Spring Boot 2.3.12
- **数据库**: MySQL 5.6
- **缓存**: Redis + Lettuce + Redisson
- **ORM**: MyBatis-Plus 3.4.3
- **工具类**: Hutool 5.7.17、Lombok

## 核心功能

### 1. 医生管理模块
- 医生信息缓存（解决缓存穿透/击穿/雪崩）
- 基于GEO的附近医生查询（5km范围）
- 科室分类浏览
- 候诊人数实时统计

### 2. 专家号抢号模块
- Lua脚本保证原子性操作
- Redis Stream消息队列实现异步挂号
- Redisson分布式锁防止重复挂号
- 全局唯一ID生成器

### 3. 就医评价模块
- 评价发布与浏览
- 点赞功能（Redis ZSet排序）
- 收藏推送（Feed流实现）
- 滚动分页查询

### 4. 医生收藏模块
- 收藏/取消收藏
- 共同收藏查询

## 核心技术亮点

### 1. Redis缓存策略
- **缓存穿透**: 缓存空值，防止恶意查询
- **缓存击穿**: 互斥锁 + 逻辑过期双方案
- **缓存更新**: 先更新数据库，再删除缓存

### 2. 专家号抢号方案
```lua
-- Lua脚本保证原子性
-- 1. 判断号源充足
-- 2. 检查是否重复挂号
-- 3. 扣减号源
-- 4. 发送消息到Stream队列
redis.call('xadd', 'stream.appointments', '*', 'patientId', patientId, 'scheduleId', scheduleId)
```

### 3. 分布式锁
```java
RLock lock = redissonClient.getLock("lock:appointment:" + patientId);
lock.tryLock(); // 防止一人多次挂号
```

### 4. 全局唯一ID生成
```java
// 时间戳(31bit) + 自增序列(32bit)
long timestamp = nowSecond - BEGIN_TIMESTAMP;
long count = stringRedisTemplate.opsForValue().increment("icr:appointment:" + date);
return timestamp << COUNT_BITS | count;
```

### 5. GEO地理位置服务
```java
// 基于经纬度查询附近医生（5km范围）
stringRedisTemplate.opsForGeo().search(
    "doctor:geo:" + departmentId,
    GeoReference.fromCoordinate(x, y),
    new Distance(5000)
)
```

### 6. Feed流推送
- 收藏医生发布动态时推送到患者收件箱（Redis ZSet）
- 滚动分页查询（避免漏读/重复读）

## 项目结构

```
hospital-queue-system
├── src/main/java/com/hmdp
│   ├── controller        # 控制器层
│   │   ├── DoctorController          # 医生管理
│   │   ├── AppointmentController     # 挂号管理
│   │   ├── ReviewController          # 评价管理
│   │   ├── FavoriteController        # 收藏管理
│   │   └── DepartmentController      # 科室管理
│   ├── service           # 服务层
│   │   ├── impl
│   │   │   ├── DoctorServiceImpl     # 医生服务实现
│   │   │   ├── AppointmentServiceImpl # 挂号服务实现
│   │   │   ├── ReviewServiceImpl      # 评价服务实现
│   │   │   └── FavoriteServiceImpl    # 收藏服务实现
│   ├── entity            # 实体类
│   │   ├── Doctor        # 医生
│   │   ├── Appointment   # 挂号订单
│   │   ├── Review        # 评价
│   │   ├── Department    # 科室
│   │   └── Schedule      # 排班
│   ├── mapper            # Mapper层
│   ├── dto               # 数据传输对象
│   └── utils             # 工具类
│       ├── CacheClient   # 缓存工具
│       ├── RedisIdWorker # ID生成器
│       └── RedisConstants # Redis常量
└── src/main/resources
    ├── application.yaml               # 配置文件
    ├── grab_schedule.lua              # 抢号Lua脚本
    └── hospital_queue_migration.sql   # 数据库迁移脚本
```

## 快速开始

### 1. 环境要求
- JDK 8+
- MySQL 5.6+
- Redis 3.0+
- Maven 3.6+

### 2. 数据库初始化
```bash
# 执行数据库迁移脚本
mysql -u root -p < src/main/resources/db/hospital_queue.sql
```

### 3. 修改配置
可直接使用默认本地配置，也可以通过环境变量覆盖：
```yaml
spring:
  datasource:
    url: jdbc:mysql://${DB_HOST}:${DB_PORT}/${DB_NAME}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  redis:
    host: ${REDIS_HOST}
    port: ${REDIS_PORT}
    password: ${REDIS_PASSWORD}
```

上传目录可通过 `UPLOAD_DIR` 配置，默认使用项目目录下的 `uploads`。

### 4. 启动项目
```bash
mvn clean package
java -jar target/hm-dianping-0.0.1-SNAPSHOT.jar
```

### 5. 访问接口
- 基础URL: `http://localhost:8081`
- 查询医生: `GET /doctor/{id}`
- 抢专家号: `POST /appointment/grab/{scheduleId}`
- 发布评价: `POST /review`
- 健康检查: `GET /actuator/health`
- Prometheus 指标: `GET /actuator/prometheus`

## API文档

### 医生相关接口

#### 查询医生详情
```http
GET /doctor/{id}
```

#### 根据科室查询医生
```http
GET /doctor/of/department?departmentId=1&current=1&x=116.23&y=39.54
```

#### 查询候诊人数
```http
GET /doctor/{id}/waiting
```

### 挂号相关接口

#### 抢专家号
```http
POST /appointment/grab/{scheduleId}
```

### 评价相关接口

#### 发布评价
```http
POST /review
Content-Type: application/json

{
  "doctorId": 1,
  "title": "服务态度很好",
  "content": "医生很专业，诊断准确",
  "images": "/imgs/review1.jpg"
}
```

#### 点赞评价
```http
PUT /review/like/{id}
```

## 可复现实测

- 医生详情预热读接口：2,000 请求、并发 20，887.11 请求/秒，错误 0。详见 `doctor-detail-load-test.md`。
- 预约抢号集成压测：100 个 HTTP 请求、并发 40、初始号源 12，成功 12、拒绝 88、HTTP 错误 0；12 条预约全部落库，Redis 与数据库号源均归零。详见 `appointment-concurrency-load-test.md`。
- 两组结果都是本机单实例测试，不代表生产容量、集群吞吐或可用性。

## 改造说明

本项目基于黑马点评项目改造而来，核心改造点：

| 原业务 | 新业务 | 技术复用 |
|--------|--------|----------|
| 商户信息 | 医生信息 | Redis缓存、GEO查询 |
| 优惠券秒杀 | 专家号抢号 | Lua脚本、分布式锁、消息队列 |
| 探店笔记 | 就医评价 | Redis ZSet点赞、Feed流 |
| 好友关注 | 医生收藏 | Redis Set交集 |
| 用户签到 | 就诊打卡 | Bitmap统计 |

## 作者

- GitHub: [你的GitHub]
- Email: [你的邮箱]

## 许可证

MIT License
