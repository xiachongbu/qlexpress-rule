package com.bjjw.rule.server.consolelogin;

import com.alibaba.fastjson.JSON;
import com.bjjw.rule.server.common.R;
import lombok.RequiredArgsConstructor;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * 在启用控制台登录时，对配置的 include 路径校验 HttpSession 中是否已登录。
 */
@RequiredArgsConstructor
public class ConsoleSessionAuthInterceptor implements HandlerInterceptor {

    private final RuleEngineConsoleLoginProperties properties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * 判断当前请求是否允许访问：未命中需保护路径则放行；已登录则放行；否则返回 401 JSON。
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!properties.isEnabled()) {
            return true;
        }
        String uri = request.getRequestURI();
        if (!matchesAny(uri, properties.getIncludePatterns())) {
            return true;
        }
        if (matchesAny(uri, properties.getExcludePatterns())) {
            return true;
        }
        HttpSession session = request.getSession(false);
        if (session == null) {
            return writeUnauthorized(response, "需要登录");
        }
        Object user = session.getAttribute(properties.getSessionUsernameAttribute());
        if (user == null) {
            return writeUnauthorized(response, "需要登录");
        }
        return true;
    }

    /**
     * 将 URI 与一组 Ant 模式做匹配，任一匹配即返回 true。
     */
    private boolean matchesAny(String uri, Iterable<String> patterns) {
        for (String p : patterns) {
            if (pathMatcher.match(p, uri)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 写入与前端 axios 拦截器一致的 JSON 结构（code/message）。
     */
    private boolean writeUnauthorized(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(JSON.toJSONString(R.fail(401, message)));
        return false;
    }
}
