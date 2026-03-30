package com.bjjw.rule.server.consolelogin;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 控制台（管理端）用户名密码登录相关配置，由 application.yml 中 rule-engine.console-login 绑定。
 * <p>
 * 未自定义 {@link ConsoleLoginAuthenticator} Bean 时，使用 {@link #builtin} 中的用户名与密码校验；
 * 自定义 Bean 时忽略 {@link #builtin}。
 * </p>
 */
@Data
@ConfigurationProperties(prefix = "rule-engine.console-login")
public class RuleEngineConsoleLoginProperties {

    /**
     * 是否启用管理端会话登录；关闭时行为与改造前一致，所有 /api/rule/** 接口不要求登录。
     */
    private boolean enabled = false;

    /**
     * 需要会话校验的路径（Ant 风格）；默认覆盖管理端 API 前缀。
     */
    private List<String> includePatterns = new ArrayList<>(Arrays.asList("/api/rule/**"));

    /**
     * 不参与会话校验的路径（Ant 风格）；如同步接口、日志上报等需被外部系统匿名调用。
     */
    private List<String> excludePatterns = new ArrayList<>(Arrays.asList(
            "/api/auth/**",
            "/api/rule/sync/**",
            "/api/rule/log/report"
    ));

    /**
     * 登录成功后写入 HttpSession 的属性名。
     */
    private String sessionUsernameAttribute = "RULE_CONSOLE_USERNAME";

    /**
     * 内置账号：仅当未注册自定义 {@link ConsoleLoginAuthenticator} 时用于登录校验。
     */
    private Builtin builtin = new Builtin();

    @Data
    public static class Builtin {
        /**
         * 内置登录用户名；与 password 同时非空时才参与校验。
         */
        private String username = "";

        /**
         * 内置密码：PLAIN 时为明文；BCRYPT 时为 BCrypt 哈希串（推荐生产环境）。
         */
        private String password = "";

        /**
         * password 字段的编码方式。
         */
        private PasswordEncoding passwordEncoding = PasswordEncoding.PLAIN;
    }

    public enum PasswordEncoding {
        BCRYPT,
        PLAIN
    }
}
