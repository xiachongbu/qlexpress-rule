# Changelog

本项目的显著变更记录。版本遵循 [SemVer](https://semver.org/)。

## [2.0.0] - 2026-09-03

### ⚠️ 破坏性变更（Breaking）

- **JDK 基线从 8 升至 21**：所有模块按 Java 21 字节码编译，JDK 8/11/17 宿主应用无法加载 `rule-engine-client` 2.x。仍在 JDK 8 的业务系统请使用 1.x（`1.x` 分支，JDK 8 终结线，仅修致命缺陷）。
- **Spring Boot 2.3.0 → 4.1.0**：`javax.*` 全量迁移至 `jakarta.*`；`spring.redis.*` 配置前缀变更为 `spring.data.redis.*`。
- **rule-engine-client 自动装配机制**：`META-INF/spring.factories` 替换为 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`，装配类改用 `@AutoConfiguration`。宿主应用需 Spring Boot 3.x+。
- **依赖坐标变更**：`mysql:mysql-connector-java` → `com.mysql:mysql-connector-j`；MyBatis-Plus 换用 `mybatis-plus-spring-boot4-starter`。
- **`ServiceImpl`/`IService` 包路径**（服务端扩展时才涉及）：`com.baomidou.mybatisplus.extension.service.*` → `com.baomidou.mybatisplus.spring.service.*`。

### ✨ 新增

- 编译启用 `-parameters`：适配 Spring Framework 6.1+ 移除字节码参数名推断后的 `@RequestParam`/`@PathVariable` 解析。
- `docs/upgrade-2.0.md`：1.x → 2.x 迁移指南。
- `docs/smoke-test.md`：发布前冒烟测试清单。

### 🔄 变更

- **前端 Vue 2.6 → Vue 3.5 + element-plus + Vite 6**：
  - 构建链 vue-cli 4/webpack/babel → Vite 6（`vue.config.js` → `vite.config.js`，dev 端口 9090 与 `/api` 代理不变）；
  - element-ui → element-plus（zh-cn locale、small 尺寸；`$message`/`$confirm`/`$alert` 保持原调用方式）；
  - vue-router 4（hash 模式不变）、vuex 4；
  - 图标沿用 element-ui 字体图标方案（`el-icon-*` 类名不变，内置兼容 css 与字体文件）；
  - LogicFlow 1.2.x 与 CodeMirror 5 保持不变（框架无关库）。
- **主要依赖升级**：Lombok 1.18.48（JDK 21 兼容）、MyBatis-Plus 3.5.17、OkHttp 4.12.0。
- 修复 `CorsConfig` 在 Spring Framework 6+ 下的 `allowedOrigins("*")` + `allowCredentials` 冲突（改用 `allowedOriginPatterns`）。
- 解开 `ConsoleLoginConfiguration` 的 Bean 循环依赖（Spring Boot 2.6+ 默认禁止）。

### 已知事项

- `rule-engine-example` 的 Kafka 消费端维持注释禁用状态，与 1.x 行为一致。
- Sass 对 `@import` 的弃用警告不影响构建，将在后续版本迁移至 `@use`。

## [1.0.0] - 2026-08-12

首个公开发布版本：Java 8 + Spring Boot 2.3 + Vue 2 技术栈，决策表/决策树/决策流/交叉表/评分卡/QL 脚本编排与 client SDK。
