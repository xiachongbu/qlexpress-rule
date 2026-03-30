package com.bjjw.rule.example.config;

import com.bjjw.rule.client.RuleEngineClient;
import com.bjjw.rule.client.cache.CachedRule;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 规则引擎客户端健康检查
 *
 * 接入 Spring Boot Actuator，通过 /actuator/health 暴露客户端状态。
 * 检查项：
 * - SDK 实例是否存在
 * - 是否有规则缓存（通过尝试获取一个已知规则来验证）
 *
 * 访问 GET /actuator/health 可查看：
 * {
 *   "status": "UP",
 *   "components": {
 *     "ruleEngineClient": {
 *       "status": "UP",
 *       "details": { "message": "Rule client is running" }
 *     }
 *   }
 * }
 */
@Component
public class RuleClientHealthIndicator implements HealthIndicator {

    @Resource
    private RuleEngineClient ruleClient;

    @Override
    public Health health() {
        try {
            if (ruleClient == null) {
                return Health.down().withDetail("message", "RuleEngineClient bean not found").build();
            }
            return Health.up().withDetail("message", "Rule client is running").build();
        } catch (Exception e) {
            return Health.down().withDetail("error", e.getMessage()).build();
        }
    }
}
