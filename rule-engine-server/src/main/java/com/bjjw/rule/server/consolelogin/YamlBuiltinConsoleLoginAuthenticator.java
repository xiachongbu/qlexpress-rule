package com.bjjw.rule.server.consolelogin;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.PostConstruct;

/**
 * 使用 yml 中 rule-engine.console-login.builtin 配置的用户名、密码做校验（PLAIN 或 BCrypt）。
 */
@Slf4j
@RequiredArgsConstructor
public class YamlBuiltinConsoleLoginAuthenticator implements ConsoleLoginAuthenticator {

    private final RuleEngineConsoleLoginProperties properties;

    /**
     * 启动时若已启用控制台登录且仍使用内置账号但未配置完整，输出告警便于排查。
     */
    @PostConstruct
    public void warnIfIncompleteBuiltin() {
        if (!properties.isEnabled()) {
            return;
        }
        RuleEngineConsoleLoginProperties.Builtin b = properties.getBuiltin();
        if (StrUtil.isBlank(b.getUsername()) || StrUtil.isBlank(b.getPassword())) {
            log.warn("rule-engine.console-login.enabled=true 但未配置 builtin.username/builtin.password，且未提供自定义 ConsoleLoginAuthenticator Bean，控制台将无法登录");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean authenticate(String username, String rawPassword) {
        if (StrUtil.isBlank(username) || rawPassword == null) {
            return false;
        }
        RuleEngineConsoleLoginProperties.Builtin b = properties.getBuiltin();
        if (StrUtil.isBlank(b.getUsername()) || StrUtil.isBlank(b.getPassword())) {
            return false;
        }
        if (!b.getUsername().trim().equals(username.trim())) {
            return false;
        }
        return matchesStored(b.getPassword(), rawPassword, b.getPasswordEncoding());
    }

    /**
     * 按配置编码比对 yml 中配置的密码与登录明文。
     */
    private boolean matchesStored(String storedPassword, String rawPassword,
                                  RuleEngineConsoleLoginProperties.PasswordEncoding encoding) {
        if (encoding == RuleEngineConsoleLoginProperties.PasswordEncoding.PLAIN) {
            return storedPassword.equals(rawPassword);
        }
        try {
            return BCrypt.checkpw(rawPassword, storedPassword);
        } catch (Exception e) {
            log.warn("BCrypt check failed: {}", e.getMessage());
            return false;
        }
    }
}
