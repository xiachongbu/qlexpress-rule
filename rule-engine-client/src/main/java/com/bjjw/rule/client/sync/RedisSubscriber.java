package com.bjjw.rule.client.sync;

import com.alibaba.fastjson.JSON;
import com.bjjw.rule.client.cache.CachedRule;
import com.bjjw.rule.client.cache.CachedRuleSet;
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

import java.nio.charset.StandardCharsets;
import java.util.Collections;

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
                    Collections.singletonList(new ChannelTopic(channel)));

            container.afterPropertiesSet();
            container.start();

            log.info("Redis subscriber started on channel: {}", channel);
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
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            handleMessage(body);
        }
    }

    private void handleMessage(String message) {
        try {
            RulePushMessage push = JSON.parseObject(message, RulePushMessage.class);

            // 防御性过滤：projectCode 与本客户端 appName 不匹配则跳过
            if (!appName.equals(push.getProjectCode())) {
                log.debug("Skipping push message for project={}, current appName={}", push.getProjectCode(), appName);
                return;
            }

            String action = push.getAction();

            if ("PUBLISH".equals(action)) {
                // 多作用域下 L1 按 ruleCode+compId 分键，推送后删除该规则全部作用域条目
                cache.removeAllForRule(push.getRuleCode());
                // 如果推送携带完整规则数据，直接预热 L1 避免冷启动延迟
                CachedRule cached = RulePushMessageConverter.toCachedRule(push);
                if (cached != null && cached.getCompiledScript() != null) {
                    cache.put(push.getRuleCode(), push.getCompId(), cached);
                }
                log.info("Rule invalidated via Redis push: {} v{} compId={}",
                        push.getRuleCode(), push.getVersion(), push.getCompId());

            } else if ("UNPUBLISH".equals(action) || "DELETE".equals(action)) {
                cache.removeAllForRule(push.getRuleCode());
                log.info("Rule removed via Redis push: {}", push.getRuleCode());

            } else if ("SET_PUBLISH".equals(action)) {
                if (push.getSetCode() != null) {
                    cache.removeAllForSet(push.getSetCode());
                    // 如果推送携带完整规则集数据，直接预热 L1
                    CachedRuleSet cachedSet = RulePushMessageConverter.toCachedRuleSet(push);
                    if (cachedSet != null) {
                        cache.putSet(push.getSetCode(), push.getCompId(), cachedSet);
                    }
                    log.info("Rule set invalidated via Redis push: {} v{} compId={}",
                            push.getSetCode(), push.getVersion(), push.getCompId());
                }
            } else if ("SET_UNPUBLISH".equals(action)) {
                if (push.getSetCode() != null) {
                    cache.removeAllForSet(push.getSetCode());
                    log.info("Rule set removed via Redis push: {}", push.getSetCode());
                }
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
