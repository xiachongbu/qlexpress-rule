package com.bjjw.rule.server.consolelogin;

/**
 * 管理端用户名密码校验扩展点。
 * <p>
 * <strong>方式一（推荐复杂场景）</strong>：在业务代码中实现本接口并注册为 Spring Bean，
 * 自行编写鉴权逻辑（多数据源、远程用户中心、LDAP 等）。
 * </p>
 * <p>
 * <strong>方式二（简单场景）</strong>：不注册本接口的 Bean，在 yml 中配置
 * {@code rule-engine.console-login.builtin.username} 与 {@code builtin.password}（支持 PLAIN 或 BCrypt）。
 * </p>
 * <p>自定义 Bean 示例：</p>
 * <pre>
 * &#64;Component
 * public class MyConsoleLoginAuthenticator implements ConsoleLoginAuthenticator {
 *     &#64;Override
 *     public boolean authenticate(String username, String rawPassword) {
 *         return false;
 *     }
 * }
 * </pre>
 */
public interface ConsoleLoginAuthenticator {

    /**
     * 校验用户名与明文密码是否匹配。
     *
     * @param username    登录用户名
     * @param rawPassword 明文密码
     * @return 校验通过返回 true
     */
    boolean authenticate(String username, String rawPassword);
}
