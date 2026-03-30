package com.bjjw.rule.server.consolelogin;

import com.bjjw.rule.server.common.R;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

/**
 * 管理端控制台登录、登出及当前用户查询；配置查询在关闭登录时仍可用。
 * 登录校验委托 {@link ConsoleLoginAuthenticator}：自定义 Bean 覆盖默认的 yml builtin 账号校验。
 */
@RestController
@RequestMapping("/api/auth/console")
public class ConsoleAuthController {

    @Resource
    private RuleEngineConsoleLoginProperties consoleLoginProperties;

    @Autowired(required = false)
    private ConsoleLoginAuthenticator consoleLoginAuthenticator;

    /**
     * 返回是否启用用户名密码登录，供前端决定是否展示登录页与携带 Cookie。
     */
    @GetMapping("/config")
    public R<Map<String, Object>> config() {
        Map<String, Object> body = new HashMap<>(2);
        body.put("loginEnabled", consoleLoginProperties.isEnabled());
        return R.ok(body);
    }

    /**
     * 校验用户名密码并建立会话；启用登录时才会校验成功。
     */
    @PostMapping("/login")
    public R<Map<String, String>> login(@RequestBody LoginRequest body, HttpServletRequest request) {
        if (!consoleLoginProperties.isEnabled()) {
            return R.fail("控制台用户名密码登录未启用");
        }
        if (consoleLoginAuthenticator == null) {
            return R.fail("登录认证组件未就绪");
        }
        if (body == null || body.getUsername() == null || body.getPassword() == null) {
            return R.fail("用户名或密码不能为空");
        }
        String username = body.getUsername().trim();
        if (username.isEmpty()) {
            return R.fail("用户名不能为空");
        }
        if (!consoleLoginAuthenticator.authenticate(username, body.getPassword())) {
            return R.fail(401, "用户名或密码错误");
        }
        HttpSession old = request.getSession(false);
        if (old != null) {
            old.invalidate();
        }
        HttpSession session = request.getSession(true);
        session.setAttribute(consoleLoginProperties.getSessionUsernameAttribute(), username);
        Map<String, String> data = new HashMap<>(2);
        data.put("username", username);
        return R.ok(data);
    }

    /**
     * 销毁当前会话。
     */
    @PostMapping("/logout")
    public R<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return R.ok();
    }

    /**
     * 查询当前登录用户名；未启用登录时返回 data 为 null；启用但未登录返回 401。
     */
    @GetMapping("/me")
    public R<Map<String, String>> me(HttpServletRequest request) {
        if (!consoleLoginProperties.isEnabled()) {
            return R.ok(null);
        }
        HttpSession session = request.getSession(false);
        if (session == null) {
            return R.fail(401, "未登录");
        }
        Object u = session.getAttribute(consoleLoginProperties.getSessionUsernameAttribute());
        if (u == null) {
            return R.fail(401, "未登录");
        }
        Map<String, String> data = new HashMap<>(2);
        data.put("username", u.toString());
        return R.ok(data);
    }

    @Data
    public static class LoginRequest {
        private String username;
        private String password;
    }
}
