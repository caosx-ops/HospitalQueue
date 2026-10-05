# 预约抢号并发验证记录

测试由 `AppointmentConcurrencyIntegrationTest` 通过真实 HTTP、Redis 和 MySQL 执行，使用临时排班、临时患者 Token，测试结束自动删除预约、排班、Token、库存和 Stream 消息。

| 项目 | 结果 |
|---|---:|
| 请求数 / 并发数 | 100 / 40 |
| 初始号源 | 12 |
| 成功预约 | 12 |
| 业务拒绝 | 88 |
| HTTP 错误 | 0 |
| 平均 / P50 / P95 / P99 | 309.294 / 131.330 / 627.018 / 628.246 ms |
| 最大延迟 | 628.303 ms |
| 吞吐 | 122.412 请求/秒 |

验证项：成功数等于号源数、预约 ID 唯一、12 条预约全部落库、Redis 和数据库剩余号源均为 0、其他患者读取预约被拒绝。完整 JSON 位于 `appointment-concurrency-load-test.json`；该文件是本次测试输出，不代表生产容量。
