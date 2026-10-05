# 测试与本地复现

## 本机依赖

测试需要 Java 8、Maven、MySQL `hospital_queue` 数据库和 Redis 6379。初始化脚本是 `src/main/resources/db/hospital_queue.sql`。数据库和 Redis 参数可通过 `DB_*`、`REDIS_*` 环境变量覆盖。

## 自动化验证

```bash
mvn test
```

测试包含缓存逻辑过期、预约事务和锁顺序、幂等、退避策略、死信补偿、登录权限边界，以及真实 HTTP 抢号并发验证。集成测试会创建临时排班和 Token，并在结束时清理测试数据。

## Docker Compose

复制 `.env.example` 为 `.env`，填入本地密码后执行：

```bash
docker compose up --build
```

应用在 `http://127.0.0.1:8081`，健康检查在 `/actuator/health`，Prometheus 指标在 `/actuator/prometheus`，Prometheus 在 `http://127.0.0.1:9090`。当前开发机未安装 Docker，因此 Compose 栈只完成配置审查，未在本机启动验证。
