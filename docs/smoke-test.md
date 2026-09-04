# 冒烟测试脚本（Smoke Test）

用于版本升级、发布前的快速验证。前置条件：MySQL（rule_engine 库已初始化）与 Redis 已启动。

## 1. 构建并启动 server

```bash
mvn clean package -DskipTests
java -jar rule-engine-server/target/rule-engine-server-<version>.jar
```

## 2. 登录冒烟

```bash
curl -s -X POST http://localhost:8080/api/auth/console/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}'
```

预期返回：`{"code":200,"message":"success","data":{"username":"admin"},...}`

## 3. 规则同步（client 拉取路径，免认证）

```bash
curl -s "http://localhost:8080/api/rule/sync/pull?appName=<你的appName>&projectId=1"
```

预期返回：`code=200` 且 `data` 中包含已发布的规则定义。

## 4. example 冒烟（验证 client SDK 端到端）

启动 `rule-engine-example`（默认 7070），调用其演示接口执行规则，预期返回规则命中结果。

## 5. Redis 订阅刷新（L1 缓存失效链路）

在控制台修改并重新发布一条规则，观察 example 日志出现规则缓存刷新记录（订阅 `rule:push:<appName>`）。

---

基线记录（v1.0.0-jdk8，JDK 8 + Spring Boot 2.3）：构建通过、登录接口 200。升级各阶段均按本清单复验。
