# 后端面试准备：医院预约排队 Demo

## 项目边界

这是一个从黑马点评改造的 Spring Boot 后端 Demo，当前没有 Web 前端、真实短信通道、支付、护士站叫号终端或生产部署。面试时按真实实现介绍，不使用未经压测或线上监控验证的规模、QPS、可用性和性能数字。

核心依赖：Java 8、Spring Boot 2.3.12、Spring MVC、MyBatis-Plus 3.4.3、MySQL、Spring Data Redis/Lettuce、Redisson、Hutool、Lombok。

## 业务映射

| 黑马点评概念 | 医院项目概念 | 复用的技术点 |
|---|---|---|
| 用户 | 患者 | 验证码登录、Redis Hash Token、拦截器续期 |
| 商户 | 医生 | 详情缓存、按科室查询、GEO 附近搜索 |
| 商户分类 | 科室 | 分类浏览和数据库分页 |
| 秒杀优惠券 | 医生排班号源 | Lua 原子校验库存、Redis Stream 异步写单 |
| 优惠券订单 | 预约记录 | Redis ID、Redisson 患者锁、MySQL 事务 |
| 探店笔记 | 就医评价 | 发布、列表、点赞 ZSet、Feed 推送 |
| 笔记评论 | 评价回复 | 回复关系与评价评论数 |
| 关注关系 | 患者收藏医生 | Redis Set 交集；语义是收藏，不是患者社交关注 |
| 用户签到 | 患者签到 | Redis Bitmap 和 BitField 连续签到统计 |
| UV | 访问 UV | Redis HyperLogLog |

## 可以重点讲的实现

### 医生缓存击穿

`DoctorServiceImpl.queryById` 实际调用 `CacheClient.queryWithLogicalExpire`。冷缓存通过带过期时间的 Redis 互斥锁完成一次数据库加载；热点数据逻辑过期后，当前请求读旧值，由一个后台任务重建缓存。缓存锁用随机 token 加 Lua 比较删除，避免请求误删别人的锁。普通 TTL 也带随机抖动；不存在的医生使用短 TTL 空值缓存。

医生资料允许缓存短暂过期，所以逻辑过期返回旧数据是有意的最终一致性取舍。这个方案不适合必须强一致的价格、库存等数据。

### 预约异步落库与失败处理

1. Lua 原子检查 Redis 号源和患者预约映射，扣减号源、登记 appointmentId，并写入 Stream。
2. 消费端持有患者维度 Redisson 锁，再调用独立的 Spring 事务服务。
3. 事务中先按预约 ID 幂等检查，再检查同一患者的活动预约，条件扣减 MySQL 号源并插入预约记录。
4. 事务代理提交后才释放 Redisson 锁；成功后 ACK Stream。
5. 临时错误最多允许 5 次落库失败；前 4 次失败后按 100/200/400/800ms 指数退避重试，第 5 次失败或遇到不可重试业务错误时写入按源消息 ID 去重的死信 Stream，再用 Lua 对 Redis 号源做一次性补偿并 ACK 原消息。死信发布失败时保留 pending，稍作暂停后再处置。
6. 应用启动会恢复 Stream 消费组、Redis 号源和活动预约映射，处理上次停机留下的 pending 消息。

这提供至少一次投递下的幂等处理，不是 Redis 与 MySQL 的分布式事务。Redis 已扣号、数据库写入失败期间会短暂不一致，最终通过重试或死信补偿收敛。演示版使用单逻辑消费者名；生产多实例还应加入可观测告警、死信人工处理流程和更完整的对账机制。

## 自动化测试与压测

`mvn test` 当前通过 20 项，覆盖缓存未过期、冷缓存加载、热点过期单任务刷新、预约锁持有顺序、重复投递幂等、数据库库存拒绝、ID 并发唯一性、HyperLogLog 误差范围、Redisson 锁语义、接口权限边界和真实 HTTP 抢号并发一致性。集成测试需要本机 MySQL 与 Redis；Docker Compose 复现方式见 `TESTING.md`。

医生详情接口压测脚本：

```bash
python scripts/load_test_doctor.py --url http://127.0.0.1:8081 --requests 2000 --concurrency 20 --output load-test-result.json
```

最新一次本机结果记录在 `doctor-detail-load-test.md` 和 `doctor-detail-load-test.json`。它只代表当次本机、单实例、预热缓存的接口探测，包含客户端和本机网络开销，不是生产容量承诺；预约抢号没有用这个读接口数据代替压测。

## 60 秒项目介绍

我做了一个从黑马点评改造的医院预约后端 Demo，技术栈是 Spring Boot、MyBatis-Plus、MySQL、Redis 和 Redisson。医生查询使用 Redis 逻辑过期缓解热点缓存击穿；预约入口通过 Lua 原子扣减 Redis 号源并写 Stream，消费者在患者锁和数据库事务里幂等落库，失败会退避重试，超过上限后进入死信并补偿 Redis 号源。Redis 还用于 GEO 附近医生、Bitmap 签到、ZSet 评价点赞、Set 收藏关系和 HyperLogLog UV。我补了缓存与预约关键分支的自动化测试，也提供了可重复的本机 HTTP 压测脚本。项目目前是可运行 Demo，不包含前端和真实短信，也没有声称经过生产级压测。

## 常见追问

### 为什么选择逻辑过期？

医生资料短暂返回旧值可以接受。过期期间请求不等待数据库；互斥锁保证只有一个后台任务回源重建。缓存未命中时仍通过短锁避免并发冷加载。代价是数据有短暂陈旧窗口，且后台刷新失败时继续返回旧值并记录日志。

### Stream 消费为什么还要幂等？

消费者可能在数据库提交后、ACK 前宕机，重启后同一消息会再次投递。预约 ID 主键先查和患者锁内的活动预约校验避免重复扣库和重复写单。

### Redis 扣号与 MySQL 写单如何保持一致？

它们没有跨存储原子事务。Redis 负责快速预占，Stream 保留待处理工作；成功提交后 ACK，临时错误保留 pending 并重试，超过限制写死信并执行带预约 ID 去重标记的补偿脚本。该方案提供最终收敛，不能宣称瞬时强一致。

### 怎么解释性能结果？

只引用 `doctor-detail-load-test.md` 和 `appointment-concurrency-load-test.md` 中实际记录的机器、请求数、并发数、延迟分位数和错误数，并明确这是本机单实例结果。预约并发验证证明了号源上限、幂等和最终落库，但不能代表生产 TPS、集群吞吐或可用性。
