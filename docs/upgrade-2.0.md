# 1.x → 2.x 迁移指南

2.0 的核心变化是技术基线升级（JDK 8 → 21、Spring Boot 2.3 → 4.1、Vue 2 → Vue 3），业务功能与数据模型无变化：**数据库表结构与存量规则数据无需任何迁移**。

对以下两类使用者，影响与操作不同：

## 一、业务系统（引入 rule-engine-client SDK 的应用）

### 前置判断

| 你的宿主环境 | 建议 |
|---|---|
| Spring Boot 3.x / 4.x + JDK 17+ | 可直接升级 client 2.x |
| Spring Boot 2.x 或 JDK 8 | 继续使用 1.x（`1.x` 分支维护线）；client 2.x 无法工作 |

原因：client 2.x 编译为 Java 21 字节码，且基于 jakarta 命名空间与 Spring Framework 6+ 编译。

### 升级步骤

1. 依赖版本改为 `2.0.0`（源码安装：`mvn clean install` 后按模块引入）。
2. 确认宿主 `application.yml` 中 Redis 配置前缀：

   ```yaml
   # 1.x（Spring Boot 2.x 前缀）
   spring.redis.host: ...
   # 2.x（Spring Boot 3.x+ 前缀，client 依赖 spring-data-redis）
   spring.data.redis.host: ...
   ```

3. 客户端配置项（`rule-engine.client.*`：server-url、app-name、token、project-id、L1 缓存等）**完全不变**。
4. Kafka 执行日志上报（可选能力）依赖 spring-kafka 4.x 传递版本，宿主无需额外适配。

### 无需改动

- Redis 订阅频道（`rule:push:{app-name}`）、L1/L2 缓存键、HTTP 同步协议均与 1.x 一致；
- 服务端 `/api/rule/sync/**` 接口契约不变，2.x server 与 1.x client 可交叉兼容运行。

## 二、二次开发者（部署 server / 前端工程）

### server（管理端）

- JDK 换 21，`mvn clean package` 即可（编译已启用 `-parameters`）；
- `spring.redis.*` → `spring.data.redis.*`（升级前请检查自己的配置覆盖）；
- MySQL 驱动坐标：`mysql:mysql-connector-java` → `com.mysql:mysql-connector-j`；
- 数据库无需变更：继续使用 `rule-engine-server/src/main/resources/sql/` 的脚本初始化或沿用存量库。

### 前端（rule-engine-builder-ui）

- Node.js 18+；启动命令：`npm run dev`（Vite，端口 9090，`/api` 代理到 8080 不变）；
- 构建命令：`npm run build`（产物仍输出至本目录 `dist/`，与 server 解耦）；
- 二次开发注意：组件 API 为 element-plus（弹窗用 `v-model`、`.sync` 修饰符已由 `v-model:xxx` 取代）。

## 三、版本策略

- `1.x` 分支：JDK 8 终结线，只修复致命缺陷，不再新增功能；
- `master`（2.x 起）：主线演进；
- 升级完成后建议按 `docs/smoke-test.md` 做一轮冒烟验证。
