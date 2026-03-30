package com.bjjw.rule.server.publish;

import com.bjjw.rule.model.dto.RulePushMessage;
import com.alibaba.fastjson.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class RulePushService {

    private static final Logger log = LoggerFactory.getLogger(RulePushService.class);
    private static final String BROADCAST_CHANNEL = "rule:push:broadcast";

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public void push(RulePushMessage message) {
        String json = JSON.toJSONString(message);
        try {
            stringRedisTemplate.convertAndSend(BROADCAST_CHANNEL, json);
            log.info("Rule pushed to Redis: {} action={}", message.getRuleCode(), message.getAction());
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
