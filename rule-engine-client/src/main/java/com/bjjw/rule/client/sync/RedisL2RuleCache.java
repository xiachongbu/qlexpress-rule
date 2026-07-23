package com.bjjw.rule.client.sync;

import com.alibaba.fastjson.JSON;
import com.bjjw.rule.client.cache.CachedRule;
import com.bjjw.rule.model.constant.RuleCompIds;
import com.bjjw.rule.model.constant.RuleRedisL2Keys;
import com.bjjw.rule.model.dto.RulePushMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 从 Redis 读取已发布规则快照（L2）；按请求 compId 先读省键再必要时回落全国键。
 */
public class RedisL2RuleCache {

    private static final Logger log = LoggerFactory.getLogger(RedisL2RuleCache.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final boolean enabled;
    private final String projectCode;

    public RedisL2RuleCache(StringRedisTemplate stringRedisTemplate, boolean enabled, String projectCode) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.enabled = enabled;
        this.projectCode = projectCode;
    }

    /**
     * 按规则编码与解析作用域读取：非全国时先试 L2 省快照，没有再试全国；全国请求只读全国键。
     *
     * @param ruleCode      规则编码
     * @param requestCompId 解析请求 compId（null/空 视为全国）
     */
    public CachedRule get(String ruleCode, String requestCompId) {
        if (!enabled || ruleCode == null || ruleCode.isEmpty()) {
            return null;
        }
        try {
            String n = RuleCompIds.normalize(requestCompId);
            if (!RuleCompIds.NATIONAL.equals(n)) {
                CachedRule hit = readOne(ruleCode, n);
                if (hit != null) {
                    return hit;
                }
            }
            return readOne(ruleCode, RuleCompIds.NATIONAL);
        } catch (Exception e) {
            log.debug("Redis L2 read failed for {}: {}", ruleCode, e.getMessage());
            return null;
        }
    }

    /**
     * 读取指定 ruleCode + compId 的 L2 快照并转为 CachedRule
     */
    private CachedRule readOne(String ruleCode, String compId) {
        String json = stringRedisTemplate.opsForValue().get(RuleRedisL2Keys.publishedRule(projectCode, ruleCode, compId));
        if (json == null || json.isEmpty()) {
            return null;
        }
        RulePushMessage msg = JSON.parseObject(json, RulePushMessage.class);
        if (msg == null || !"PUBLISH".equals(msg.getAction())) {
            return null;
        }
        return RulePushMessageConverter.toCachedRule(msg);
    }
}
