package com.bjjw.rule.client.sync;

import com.alibaba.fastjson.JSON;
import com.bjjw.rule.client.cache.CachedRuleSet;
import com.bjjw.rule.model.constant.RuleCompIds;
import com.bjjw.rule.model.constant.RuleRedisL2Keys;
import com.bjjw.rule.model.dto.RulePushMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis L2：已发布规则集快照（与单规则 L2 并列）
 */
public class RedisL2RuleSetCache {

    private static final Logger log = LoggerFactory.getLogger(RedisL2RuleSetCache.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final boolean enabled;
    private final String projectCode;

    public RedisL2RuleSetCache(StringRedisTemplate stringRedisTemplate, boolean enabled, String projectCode) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.enabled = enabled;
        this.projectCode = projectCode;
    }

    public CachedRuleSet get(String setCode, String requestCompId) {
        if (!enabled || setCode == null || setCode.isEmpty()) {
            return null;
        }
        try {
            String n = RuleCompIds.normalize(requestCompId);
            if (!RuleCompIds.NATIONAL.equals(n)) {
                CachedRuleSet hit = readOne(setCode, n);
                if (hit != null) {
                    return hit;
                }
            }
            return readOne(setCode, RuleCompIds.NATIONAL);
        } catch (Exception e) {
            log.debug("Redis L2 rule set read failed for {}: {}", setCode, e.getMessage());
            return null;
        }
    }

    private CachedRuleSet readOne(String setCode, String compId) {
        String json = stringRedisTemplate.opsForValue().get(RuleRedisL2Keys.publishedSet(projectCode, setCode, compId));
        if (json == null || json.isEmpty()) {
            return null;
        }
        RulePushMessage msg = JSON.parseObject(json, RulePushMessage.class);
        if (msg == null || !"SET_PUBLISH".equals(msg.getAction())) {
            return null;
        }
        return RulePushMessageConverter.toCachedRuleSet(msg);
    }
}
