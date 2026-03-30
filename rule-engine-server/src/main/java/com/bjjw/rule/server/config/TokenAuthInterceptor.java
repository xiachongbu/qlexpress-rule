package com.bjjw.rule.server.config;

import com.bjjw.rule.model.entity.RuleProject;
import com.bjjw.rule.server.service.RuleProjectService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Token认证拦截器
 * 用于验证客户端请求的Token有效性
 */
@Slf4j
@Component
public class TokenAuthInterceptor implements HandlerInterceptor {
    
    @Autowired
    private RuleProjectService projectService;
    
    // 需要验证Token的路径（同步API）
    private static final String[] PROTECTED_PATHS = {
        "/api/sync/"
    };
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();
        
        // 检查是否是需要保护的路径
        boolean needAuth = false;
        for (String path : PROTECTED_PATHS) {
            if (uri.startsWith(path)) {
                needAuth = true;
                break;
            }
        }
        
        // 不需要认证的路径直接放行
        if (!needAuth) {
            return true;
        }
        
        // 获取Token
        String token = extractToken(request);
        if (token == null) {
            log.warn("Token not found in request: {}", uri);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"code\":401,\"message\":\"Token is required\"}");
            return false;
        }
        
        // 验证Token
        RuleProject project = projectService.validateToken(token);
        if (project == null) {
            log.warn("Invalid token for request: {}", uri);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"code\":401,\"message\":\"Invalid or expired token\"}");
            return false;
        }
        
        // 将项目信息存入request属性，供后续使用
        request.setAttribute("projectId", project.getId());
        request.setAttribute("projectCode", project.getProjectCode());
        
        return true;
    }
    
    /**
     * 从请求中提取Token
     * 支持Header: X-Rule-Token 或 Query Param: token
     */
    private String extractToken(HttpServletRequest request) {
        // 优先从Header获取
        String token = request.getHeader("X-Rule-Token");
        if (token != null && !token.isEmpty()) {
            return token;
        }
        
        // 从Query Param获取
        token = request.getParameter("token");
        if (token != null && !token.isEmpty()) {
            return token;
        }
        
        return null;
    }
}
