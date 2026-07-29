package com.bjjw.rule.server.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.bjjw.rule.server.consolelogin.RuleEngineConsoleLoginProperties;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;

@Configuration
public class MybatisPlusConfig {

    @Resource
    private RuleEngineConsoleLoginProperties consoleLoginProperties;

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
                this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
                String userName = currentUserName();
                if (userName != null) {
                    this.strictInsertFill(metaObject, "createBy", String.class, userName);
                    this.strictInsertFill(metaObject, "updateBy", String.class, userName);
                }
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                // 实体可能是 getById 查出后再更新，带有旧值，strict 填充会跳过，故强制覆盖
                this.setFieldValByName("updateTime", LocalDateTime.now(), metaObject);
                String userName = currentUserName();
                if (userName != null) {
                    this.setFieldValByName("updateBy", userName, metaObject);
                }
            }

            /**
             * 从当前请求会话取控制台登录用户名；SDK/定时任务等无登录态场景返回 null，不填充。
             */
            private String currentUserName() {
                ServletRequestAttributes attributes =
                        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attributes == null) {
                    return null;
                }
                HttpSession session = attributes.getRequest().getSession(false);
                if (session == null) {
                    return null;
                }
                Object username = session.getAttribute(consoleLoginProperties.getSessionUsernameAttribute());
                return username == null ? null : username.toString();
            }
        };
    }
}
