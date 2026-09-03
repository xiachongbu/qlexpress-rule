package com.bjjw.rule.server.publish;

import com.bjjw.rule.model.dto.RulePushMessage;
import com.alibaba.fastjson.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

@Service
public class RulePushService {

    private static final Logger log = LoggerFactory.getLogger(RulePushService.class);

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 推送规则变更消息到项目专属频道 rule:push:{projectCode}
     */
    public void push(RulePushMessage message) {
        String json = JSON.toJSONString(message);
        String channel = "rule:push:" + message.getProjectCode();
        try {
            stringRedisTemplate.convertAndSend(channel, json);
            String label = message.getRuleCode() != null && !message.getRuleCode().isEmpty()
                    ? message.getRuleCode() : message.getSetCode();
            log.info("Rule pushed to Redis channel={}: {} action={}", channel, label, message.getAction());
        } catch (Exception e) {
            log.error("Failed to push rule to Redis: {}", e.getMessage(), e);
        }
    }

    public void pushToApp(String appName, RulePushMessage message) {
        String channel = "rule:push:" + appName;
        String json = JSON.toJSONString(message);
        try {
            stringRedisTemplate.convertAndSend(channel, json);
        } catch (Exception e) {
            log.error("Failed to push rule to app {}: {}", appName, e.getMessage(), e);
        }
    }
}
