package com.bjjw.rule.client.sync;

import com.alibaba.fastjson.JSON;
import com.bjjw.rule.client.cache.CachedRule;
import com.bjjw.rule.client.cache.L1MemoryCache;
import com.bjjw.rule.client.function.ClientFunctionRegistrar;
import com.bjjw.rule.model.dto.RulePushMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.util.Arrays;

/**
 * Redis订阅器 - 使用Spring Data Redis实现
 * 支持规则推送（PUBLISH/UNPUBLISH）和函数推送（FUNC_UPDATE/FUNC_DELETE）
 */
public class RedisSubscriber {

    private static final Logger log = LoggerFactory.getLogger(RedisSubscriber.class);

    private final L1MemoryCache cache;
    private final RedisConnectionFactory connectionFactory;
    private final String appName;
    private final String channel;
    private RedisMessageListenerContainer container;
    private ClientFunctionRegistrar functionRegistrar;

    public RedisSubscriber(L1MemoryCache cache, RedisConnectionFactory connectionFactory, String appName) {
        this.cache = cache;
        this.connectionFactory = connectionFactory;
        this.appName = appName;
        this.channel = "rule:push:" + appName;
    }

    /**
     * 设置函数注册器，用于处理 FUNC_UPDATE 推送
     */
    public void setFunctionRegistrar(ClientFunctionRegistrar functionRegistrar) {
        this.functionRegistrar = functionRegistrar;
    }

    /**
     * 启动Redis订阅
     */
    public void start() {
        try {
            container = new RedisMessageListenerContainer();
            container.setConnectionFactory(connectionFactory);

            container.addMessageListener(new RuleMessageListener(),
                    Arrays.asList(new ChannelTopic(channel), new ChannelTopic("rule:push:broadcast")));

            container.afterPropertiesSet();
            container.start();

            log.info("Redis subscriber started on channels: {}, rule:push:broadcast", channel);
        } catch (Exception e) {
            log.error("Failed to start Redis subscriber: {}", e.getMessage(), e);
        }
    }

    /**
     * 停止Redis订阅
     */
    public void stop() {
        if (container != null) {
            container.stop();
        }
    }

    private class RuleMessageListener implements MessageListener {
        @Override
        public void onMessage(Message message, byte[] pattern) {
            String body = new String(message.getBody());
            handleMessage(body);
        }
    }

    private void handleMessage(String message) {
        try {
            RulePushMessage push = JSON.parseObject(message, RulePushMessage.class);
            String action = push.getAction();

            if ("PUBLISH".equals(action)) {
                CachedRule cached = new CachedRule();
                cached.setRuleCode(push.getRuleCode());
                cached.setProjectCode(push.getProjectCode());
                cached.setVersion(push.getVersion() != null ? push.getVersion() : 0);
                cached.setModelType(push.getModelType());
                cached.setCompiledScript(push.getCompiledScript());
                cached.setCompiledType(push.getCompiledType());
                cached.setModelJson(push.getModelJson());
                cached.setLastUpdateTime(System.currentTimeMillis());
                cache.put(cached);
                log.info("Rule updated via Redis push: {} v{}", push.getRuleCode(), push.getVersion());

            } else if ("UNPUBLISH".equals(action) || "DELETE".equals(action)) {
                cache.remove(push.getRuleCode());
                log.info("Rule removed via Redis push: {}", push.getRuleCode());

            } else if ("FUNC_UPDATE".equals(action)) {
                if (functionRegistrar != null && push.getFuncCode() != null) {
                    functionRegistrar.registerFromPush(
                            push.getFuncCode(), push.getFuncImplType(),
                            push.getFuncImplScript(), push.getFuncImplClass(),
                            push.getFuncImplMethod(), push.getFuncImplBeanName(),
                            push.getFuncParamsJson());
                    log.info("Function updated via Redis push: {} ({})", push.getFuncCode(), push.getFuncImplType());
                }

            } else if ("FUNC_DELETE".equals(action)) {
                log.info("Function delete received via Redis push: {} (runtime removal not supported, restart to apply)",
                        push.getFuncCode());
            }
        } catch (Exception e) {
            log.warn("Failed to handle Redis push message: {}", e.getMessage());
        }
    }
}
