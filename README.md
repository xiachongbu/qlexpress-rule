# QLExpress 可视化规则引擎项目使用说明

---

## 1. 项目简介

**qlexpress-rule** 是一套基于 **Spring Boot 2.3** 与 **QLExpress 4** 的可视化规则引擎：在 Web 控制台中编排 **决策表、决策树、决策流、交叉表、评分卡、复杂交叉表、复杂评分卡、QL 脚本** 等模型，编译发布后由 **rule-engine-client** SDK 在业务系统中执行；支持 Redis 推送规则变更、可选 Kafka 执行日志等扩展。**部署拓扑与业务侧集成步骤见 §3。**

---

## 2. 模块说明

| 模块 | 说明 |
|------|------|
| `rule-engine-model` | 公共实体与 DTO |
| `rule-engine-core` | 规则编译与执行核心 |
| `rule-engine-server` | 管理端 **REST API**（规则同步、日志等），默认 **8080**；与前端工程解耦，**不再**将 Vue 构建结果输出到本模块目录 |
| `rule-engine-builder-ui` | Vue 2 **独立前端**：**`npm run build`** |
| `rule-engine-client` | 客户端 SDK（HTTP 拉取 + Redis 订阅） |
| `rule-engine-example` | 集成示例服务，默认 **7070**，演示多种模型与函数类型调用 |

---

## 3. 系统架构与客户端集成

从部署拓扑与运行时行为说明 **管理端、MySQL、Redis、业务应用** 如何协作，以及如何在 Spring Boot 中引入 **rule-engine-client**。

### 3.1 部署拓扑

```mermaid
flowchart TB
  subgraph User["使用方"]
    Browser["浏览器"]
  end
  subgraph Frontend["rule-engine-builder-ui（独立前端）"]
    UI["管理控制台\nnpm run build → dist"]
  end
  subgraph Server["rule-engine-server（默认 :8080）"]
    API["REST API\n规则同步 / 日志等"]
  end
  subgraph Store["持久化"]
    MySQL[("MySQL")]
  end
  subgraph Infra["消息与缓存"]
    Redis[("Redis\nPub/Sub")]
  end
  subgraph Biz["业务应用（你的服务）"]
    App["Spring Boot\n业务代码"]
    SDK["rule-engine-client\n+ L1 规则缓存"]
  end
  Browser --> UI
  Browser --> API
  API --> MySQL
  API --> Redis
  SDK -->|HTTP：全量 / 单条拉取规则| API
  SDK -->|SUBSCRIBE\nrule:push:appName| Redis
  API -->|发布/下线/函数变更\nPUBLISH| Redis
  SDK -->|可选：执行日志上报| API
  App --> SDK
```

要点：

- **管理控制台**由 **`rule-engine-builder-ui`** 单独构建、单独部署（产物在 **`dist/`**），浏览器访问静态资源与 **`rule-engine-server`** API；开发环境可用前端 **`npm run dev`**（默认 **9090**）将 **`/api`** 代理到后端 **8080**。
- 业务应用**不直连 MySQL** 读取规则定义；通过 **HTTP** 拉取服务端已编译规则，缓存在进程内 **L1**。
- **Redis 必须与 rule-engine-server 使用同一实例**（含密码、database）。服务端在规则发布等事件时向频道 **`rule:push:{appName}`** 发布消息；客户端使用配置项 **`app-name`** 订阅对应频道，用于实时更新本地缓存。
- **执行日志**：默认通过 **HTTP** 向服务端批量上报；若 classpath 中存在 **`KafkaTemplate`** Bean 且未自行提供 **`ExecutionLogReporter`** Bean，自动改用 **Kafka**（主题默认 **`rule-execution-log`**，可用 `rule-engine.client.kafka-log-topic` 覆盖）。

### 3.2 启动、执行与推送（时序示意）

```mermaid
sequenceDiagram
  participant Biz as 业务代码
  participant C as RuleEngineClient
  participant L1 as L1 缓存
  participant HTTP as Server 同步 API
  participant Redis as Redis
  Biz->>C: Spring 注入后 start()
  C->>HTTP: 全量同步规则
  HTTP-->>C: CachedRule 列表
  C->>L1: 写入缓存
  C->>Redis: 订阅 rule:push:appName
  loop 定时心跳（默认 5 分钟）
    C->>HTTP: 全量同步（兜底）
  end
  Biz->>C: execute(ruleCode, Map 或 DTO)
  C->>L1: get(ruleCode)
  alt 缓存未命中
    C->>HTTP: 单条拉取
    HTTP-->>C: CachedRule
    C->>L1: put
  end
  C->>C: QLExpress 本地执行
  C-->>Biz: RuleResult
  C->>C: 异步上报执行日志（若启用）
  Redis-->>C: 规则/函数变更消息
  C->>C: 更新或失效缓存
```

### 3.3 Maven 依赖与自动配置

在业务工程 `pom.xml` 中依赖 **`rule-engine-client`**，并引入 **`spring-boot-starter-data-redis`**（Lettuce 连接池需 **`commons-pool2`**），坐标与版本可参考 **`rule-engine-example/pom.xml`**。

**`META-INF/spring.factories`** 注册 **`RuleEngineAutoConfiguration`**：当配置了 **`rule-engine.client.server-url`** 且存在 **`RedisConnectionFactory`** Bean 时，会创建 **`RuleEngineClient`** 并在启动时 **`start()`**（订阅 Redis、全量同步规则；若 **`project-id` > 0** 则同步控制台「函数管理」中的函数定义）。

### 3.4 application.yml 配置示例

```yaml
rule-engine:
  client:
    server-url: http://localhost:8080
    app-name: your-service-name
    token: <与控制台项目「访问令牌」一致>
    project-id: 1
    # l1-cache-max-size: 1000
    # http-timeout-ms: 3000
    # trace-enabled: true
    # kafka-log-topic: rule-execution-log  # 使用 Kafka 上报执行日志时（需存在 KafkaTemplate Bean）

spring:
  redis:
    host: localhost
    port: 6379
    password: <与 server 一致>
```

**`token`、`project-id`** 在控制台 **「规则项目」** 中查看；HTTP 同步接口的鉴权方式见下文 **「客户端鉴权（Token）」**。

### 3.5 代码中调用

注入 **`com.bjjw.rule.client.RuleEngineClient`**，使用控制台中的 **规则编码** 与入参执行（Map 的 key 或 **DTO 字段名** 对应表达式变量名）：

```java
@Resource
private RuleEngineClient ruleClient;

public void example() {
    Map<String, Object> params = new HashMap<>();
    params.put("amount", 10000);
    RuleResult r = ruleClient.execute("YOUR_RULE_CODE", params);
    // 或：ruleClient.execute("YOUR_RULE_CODE", queryDto);
}
```

常用方法：**`refreshRule` / `refreshAll`** 主动刷新缓存；**`getRuleInfo`** 查看本地缓存元数据；**`getEngine` / `getFunctionRegistrar`** 用于扩展自定义函数（高级用法）。

### 3.6 与示例工程的关系

**`rule-engine-example`**（默认端口 **7070**）中的 **`RuleExampleController`**（`/api/example`）覆盖多种模型与函数类型的 REST 示例，可与本文 **§3** 对照阅读；启动前请按该模块的 **`application.yml`** 配置 **`server-url`、`token`、`project-id`** 以及与 server 一致的 **Redis**。

---

## 4. 环境要求

- **JDK 8**
- **Maven 3.6+**
- **MySQL 8**（库名示例：`rule_engine`）
- **Redis**（与 server、client 示例共用，用于规则推送）
- 构建带前端时：**Node.js**（与 `rule-engine-server/pom.xml` 中 `frontend-maven-plugin` 一致时为 **22.14.x**；本地也可使用 **14+** 以满足 `vue-cli-service`）

---

## 5. 数据库初始化

1. 在 MySQL 中执行建表脚本：

   `rule-engine-server/src/main/resources/sql/schema.sql`

2. （可选）导入示例数据：

   `rule-engine-server/src/main/resources/sql/data-example.sql`

3. 修改服务端数据源（或使用环境变量覆盖），见 `rule-engine-server/src/main/resources/application.yml` 中的 `spring.datasource` 与 `spring.redis`。

---

## 6. 启动规则引擎服务端与管理控制台

后端与前端**分开启动、分开部署**：Vue **不**构建进 `rule-engine-server` 目录，**不**做混淆。

### 6.1 后端 API（rule-engine-server）

在仓库根目录执行：

```bash
cd rule-engine-server
mvn spring-boot:run
```

或直接运行主类：`com.bjjw.rule.server.RuleEngineApplication`。

- 默认 **http://localhost:8080/**，对外主要是 **REST API**（规则同步、日志等）。
- 当前约定下前端产物**不会**输出到本模块；**`rule-engine-server/pom.xml`** 中 **`skip.ui.build`** 默认为 **`true`**，Maven **不会**在 **`generate-resources`** 阶段执行 `rule-engine-builder-ui` 的 **`npm run build`**。若需在 CI 中由 Maven 触发前端打包，可将其改为 **`false`**（产物仍在 **`dist/`**，需自行部署）。

### 6.2 管理控制台（rule-engine-builder-ui）

源码目录：**`rule-engine-builder-ui/`**。

| 场景 | 说明 |
|------|------|
| 本地开发 | **`npm install`** 后执行 **`npm run dev`**（默认 **9090**；**`/api`** 代理到 **http://localhost:8080**） |
| 生产部署 | **`npm run build`**，将 **`dist/`** 交给 Nginx、静态站点或对象存储等托管；与后端**目录解耦**，**无** javascript-obfuscator 等混淆，仅 Vue CLI 默认压缩 |

浏览器访问控制台时，确保静态站点能访问到后端 API（开发时由 devServer 代理；生产需按你的网关/域名配置接口地址或同源反代）。

---

## 7. 控制台登录

默认在 `application.yml` 中开启控制台登录（`rule-engine.console-login.enabled: true`），内置账号可通过环境变量修改：

- **`CONSOLE_USERNAME`**（默认 `admin`）
- **`CONSOLE_PASSWORD`**（默认 `admin`）

本地开发时控制台入口为前端 devServer：**http://localhost:9090/**（见 **§6.2**）；未登录会跳转登录页。若你将 **`dist/`** 与 API 配成同源（例如统一域名反代），则入口以你的部署地址为准。

![控制台登录页](docs/project-usage/project-usage-01-login.png)

---

## 8. 规则项目列表

登录后左侧菜单进入 **「规则项目」**：可检索、新建、编辑、删除项目，并通过 **「进入」** 打开项目内规则列表。项目上的 **访问令牌（Token）** 供客户端 SDK 调用同步接口时校验（见后文）。

![规则项目列表](docs/project-usage/project-usage-02-project-list.png)

---

## 9. 项目内规则管理

在项目详情页可查看规则编码、名称、**模型类型**、发布状态、设计/发布版本；可对单条规则进行 **设计、重新发布、下线、删除**，或 **新建规则**。

![项目内规则列表](docs/project-usage/project-usage-07-project-detail.png)

**模型类型与路由对应关系（设计器路径）：**

| 类型 | 前端路由示例 |
|------|----------------|
| 决策表 | `#/designer/table/{definitionId}` |
| 决策树 | `#/designer/tree/{definitionId}` |
| 决策流 | `#/designer/flow/{definitionId}` |
| 交叉表 | `#/designer/cross/{definitionId}` |
| 评分卡 | `#/designer/score/{definitionId}` |
| 复杂交叉表 | `#/designer/cross-adv/{definitionId}` |
| 复杂评分卡 | `#/designer/score-adv/{definitionId}` |
| QL 脚本 | `#/designer/script/{definitionId}` |

---

## 10. 各模型设计器（示例数据）

在项目规则列表中点击 **「设计」** 进入对应类型的可视化编排界面；工具栏通常包含 **保存、编译、测试** 等能力。下列截图来自示例库 **`data-example.sql`** 中 **综合风控示例项目** 的已发布规则（`definition_id` 与路由见下表），本地环境需已导入该脚本，并启动 **rule-engine-server**（API）与 **`rule-engine-builder-ui`** 的 **`npm run dev`**（控制台，见 **§6.2**）。

| 模型 | 示例规则（规则编码） | 设计器路由 |
|------|----------------------|------------|
| 决策表 | `RC_PRICING_TABLE`（客商×产品总线定价表） | `#/designer/table/1` |
| 决策树 | `RC_CREDIT_TREE`（客户信用分层） | `#/designer/tree/2` |
| 决策流 | `RC_EXPOSURE_FLOW`（敞口与费用试算流程） | `#/designer/flow/3` |
| 交叉表 | `RC_RATE_MATRIX`（风险定价交叉表） | `#/designer/cross/4` |
| 评分卡 | `RC_RISK_SCORECARD`（综合风险评分卡） | `#/designer/score/5` |
| 复杂交叉表 | `RC_MULTI_DIM_RATE`（交叉矩阵多维定价 8×6） | `#/designer/cross-adv/9` |
| 复杂评分卡 | `RC_INVOICE_FRAUD_SCORE`（交易票据异常评分） | `#/designer/score-adv/10` |
| QL 脚本 | `RC_BLEND_CALC_SCRIPT`（混业组合计费脚本） | `#/designer/script/11` |

仓库提供脚本 **`scripts/capture-designer-screenshots.cjs`**（依赖 **`scripts/`** 下已执行 `npm install` 与 `npx playwright install chromium`），请将脚本中的控制台基址配为与当前部署一致（开发时一般为 **http://localhost:9090**），再自动登录并批量导出截图至 **`docs/project-usage/project-usage-designer-*.png`**。

### 10.1 决策表（TABLE）

可维护命中策略、条件（IF）、动作（THEN）等。

![决策表设计器](docs/project-usage/project-usage-designer-table.png)

### 10.2 决策树（TREE）

树形节点与分支条件编排。

![决策树设计器](docs/project-usage/project-usage-designer-tree.png)

### 10.3 决策流（FLOW）

流程节点、连线与脚本/任务步骤。

![决策流设计器](docs/project-usage/project-usage-designer-flow.png)

### 10.4 交叉表（CROSS）

行×列矩阵与交叉单元结果。

![交叉表设计器](docs/project-usage/project-usage-designer-cross.png)

### 10.5 评分卡（SCORE）

指标、权重与评分汇总。

![评分卡设计器](docs/project-usage/project-usage-designer-score.png)

### 10.6 复杂交叉表（CROSS_ADV）

多维度交叉（如 ICT 场景下的多维定价矩阵）。

![复杂交叉表设计器](docs/project-usage/project-usage-designer-cross-adv.png)

### 10.7 复杂评分卡（SCORE_ADV）

分组指标与复杂加权策略。

![复杂评分卡设计器](docs/project-usage/project-usage-designer-score-adv.png)

### 10.8 QL 脚本（SCRIPT）

脚本编辑与编译执行。

![QL 脚本设计器](docs/project-usage/project-usage-designer-script.png)

---

## 11. 变量管理

菜单 **「变量管理」**：请先 **选择项目**，再维护变量列表、数据对象、常量等；支持批量导入（如 Java 实体、JSON、DDL 等，以页面实际选项为准）与 **验证规则**。

![变量管理](docs/project-usage/project-usage-03-variable.png)

---

## 12. 函数管理

菜单 **「函数管理」**：按项目维护可在决策流/决策树脚本任务中调用的自定义函数，实现方式支持 **QLExpress 脚本、Java 类、Spring Bean** 等（见页面说明）。

![函数管理](docs/project-usage/project-usage-04-function.png)

---

## 13. 规则测试

菜单 **「规则测试」**：选择 **项目** 与 **已发布规则**，可 **加载项目变量** 填充入参，编辑后点击 **「执行测试」** 查看返回结果与追踪信息。

![规则测试](docs/project-usage/project-usage-05-rule-test.png)

---

## 14. 执行日志

菜单 **「执行日志」**：按来源（服务端/客户端）、项目、规则、时间范围筛选；列表中可查看耗时、结果、追踪摘要，**「详情」** 查看单次执行明细。

![执行日志](docs/project-usage/project-usage-06-execution-log.png)

### 14.1 日志详情与「表达式追踪树」

在列表中点击某次执行的 **「详情」** 打开弹窗；默认在 **「基本信息」**，切换到 **「表达式追踪树」** 可查看逐步判定、赋值与汇总等追踪信息（与规则测试中的追踪类似，便于对照线上/客户端执行结果）。

**推荐操作：** 在 **「全部规则」** 中输入规则名称关键字，用键盘 **↓** 选中后 **Enter** 确认，再点 **「查询」**，可避免下拉项被表格遮挡。若弹窗异常无法关闭，可尝试 **Esc** 或刷新 **#/log** 页面。

下列截图按 **模型类型** 与示例数据中的规则名称对应（以你环境中的实际规则为准）。

#### 决策表（TABLE）— 客群×产品线定价表

![执行日志 · 表达式追踪树 · 决策表](docs/project-usage/project-usage-log-trace-TABLE.png)

#### 决策树（TREE）— 客户信用分层

![执行日志 · 表达式追踪树 · 决策树](docs/project-usage/project-usage-log-trace-TREE.png)

#### 决策流（FLOW）— 敞口与费用试算流程

![执行日志 · 表达式追踪树 · 决策流](docs/project-usage/project-usage-log-trace-FLOW.png)

#### 交叉表（CROSS）— 二维风险参数矩阵

![执行日志 · 表达式追踪树 · 交叉表](docs/project-usage/project-usage-log-trace-CROSS.png)

#### 评分卡（SCORE）— 综合风险评分卡

![执行日志 · 表达式追踪树 · 评分卡](docs/project-usage/project-usage-log-trace-SCORE.png)

#### 复杂交叉表（CROSS_ADV）— 多维场景定价矩阵

![执行日志 · 表达式追踪树 · 复杂交叉表](docs/project-usage/project-usage-log-trace-CROSS_ADV.png)

#### 复杂评分卡（SCORE_ADV）— 交易票据异常评分

![执行日志 · 表达式追踪树 · 复杂评分卡](docs/project-usage/project-usage-log-trace-SCORE_ADV.png)

#### QL 脚本（SCRIPT）— 混业组合计费脚本

![执行日志 · 表达式追踪树 · QL 脚本](docs/project-usage/project-usage-log-trace-SCRIPT.png)

---

## 15. 客户端示例工程（rule-engine-example）

**§3** 已说明架构、依赖与 **`RuleEngineClient`** 用法；本小节说明如何启动仓库自带的可运行示例：

1. 确保 **rule-engine-server** 已启动，且 Redis、MySQL 可用，规则已发布。
2. 配置 `rule-engine-example/src/main/resources/application.yml`：
   - `rule-engine.client.server-url`：服务端地址（默认 `http://localhost:8080`）
   - `rule-engine.client.token`：与项目 **访问令牌** 一致
   - `rule-engine.client.project-id`：与控制台中的项目 ID 一致
   - `spring.redis`：与 **server 使用同一 Redis**（示例中默认密码可能与 server 不同，请按环境统一修改）

启动示例应用（主类 `com.bjjw.rule.example.RuleExampleApplication`），默认端口 **7070**。

示例 REST 前缀为 **`/api/example`**，覆盖决策表/树/流/交叉表/评分卡、复杂模型、脚本、JAVA/BEAN/SCRIPT 函数、通用 `execute` 与缓存刷新等，详见 `RuleExampleController` 内注释与映射。

---

## 16. 客户端鉴权（Token）

`TokenAuthInterceptor` 对 **`/api/sync/`** 等路径校验令牌：请求头 **`X-Rule-Token`** 或 Query **`token`**，须与 `rule_project.access_token` 匹配。业务应用通过 SDK 同步规则时需配置与控制台一致的 Token。

---

## 17. 关闭控制台登录（开发环境）

将 `rule-engine-server` 的 `application.yml` 中 **`rule-engine.console-login.enabled`** 设为 `false` 后，前端路由守卫将不再强制跳转登录页（适用于内网调试；生产环境请保持开启并改用强密码或自定义认证）。

---

## 18. 许可与声明

项目根目录下的 `LICENSE`、`NOTICE` 等文件适用于本仓库；第三方组件以各模块 `pom.xml` 及构建产物为准。

若文档与代码不一致，以当前分支源码与配置为准。
