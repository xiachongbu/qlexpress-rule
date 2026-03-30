package com.bjjw.rule.server.consolelogin;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 始终注册控制台登录配置属性，便于在未启用登录时仍提供 /api/auth/console/config 等接口。
 */
@Configuration
@EnableConfigurationProperties(RuleEngineConsoleLoginProperties.class)
public class ConsoleLoginPropertiesConfiguration {
}
