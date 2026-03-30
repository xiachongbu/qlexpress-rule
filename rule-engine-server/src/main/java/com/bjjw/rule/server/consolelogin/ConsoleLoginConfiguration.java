package com.bjjw.rule.server.consolelogin;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 启用 rule-engine.console-login.enabled=true 时注册会话拦截器；若不存在自定义 {@link ConsoleLoginAuthenticator}，
 * 再注册基于 yml builtin 的默认认证实现。
 */
@Configuration
@ConditionalOnProperty(prefix = "rule-engine.console-login", name = "enabled", havingValue = "true")
public class ConsoleLoginConfiguration implements WebMvcConfigurer {

    @Autowired
    private ConsoleSessionAuthInterceptor consoleSessionAuthInterceptor;

    @Autowired
    private RuleEngineConsoleLoginProperties ruleEngineConsoleLoginProperties;

    /**
     * 默认使用 yml 中 builtin 用户名密码；存在自定义 {@link ConsoleLoginAuthenticator} Bean 时不注册。
     */
    @Bean
    @ConditionalOnMissingBean(ConsoleLoginAuthenticator.class)
    public ConsoleLoginAuthenticator yamlBuiltinConsoleLoginAuthenticator(RuleEngineConsoleLoginProperties properties) {
        return new YamlBuiltinConsoleLoginAuthenticator(properties);
    }

    /**
     * 会话校验拦截器 Bean。
     */
    @Bean
    public ConsoleSessionAuthInterceptor consoleSessionAuthInterceptor(RuleEngineConsoleLoginProperties properties) {
        return new ConsoleSessionAuthInterceptor(properties);
    }

    /**
     * 将控制台登录拦截器挂到 MVC；与 {@link com.bjjw.rule.server.config.WebMvcConfig} 中全局拦截器共存。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(consoleSessionAuthInterceptor)
                .addPathPatterns(ruleEngineConsoleLoginProperties.getIncludePatterns())
                .excludePathPatterns(ruleEngineConsoleLoginProperties.getExcludePatterns());
    }
}
