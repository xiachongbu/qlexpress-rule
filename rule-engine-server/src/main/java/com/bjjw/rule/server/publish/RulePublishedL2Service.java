package com.bjjw.rule.server.publish;

import com.alibaba.fastjson.JSON;
import com.bjjw.rule.model.constant.RuleCompIds;
import com.bjjw.rule.model.constant.RuleRedisL2Keys;
import com.bjjw.rule.model.dto.RulePushMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.Set;

/**
 * 已发布规则的 Redis L2 缓存：与 Pub/Sub 推送体一致，便于客户端在 L1 未命中时直接反序列化
 */
@Service
public class RulePublishedL2Service {

    private static final Logger log = LoggerFactory.getLogger(RulePublishedL2Service.class);

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 规则发布成功后写入 Redis 快照（无 TTL，下线时删除）；并登记 compId 到索引 SET 便于按 ruleCode 批量清理
     *
     * @param message 与推送到订阅频道相同结构的载荷
     */
    public void savePublishedSnapshot(RulePushMessage message) {
        if (message == null || message.getRuleCode() == null || message.getRuleCode().isEmpty()) {
            return;
        }
        try {
            String projectCode = message.getProjectCode();
            String compId = RuleCompIds.normalize(message.getCompId());
            message.setCompId(compId);
            String key = RuleRedisL2Keys.publishedRule(projectCode, message.getRuleCode(), compId);
            stringRedisTemplate.opsForValue().set(key, JSON.toJSONString(message));
            stringRedisTemplate.opsForSet().add(RuleRedisL2Keys.publishedScopesIndex(projectCode, message.getRuleCode()), compId);
            log.debug("Rule L2 cache saved: {} projectCode={} compId={}", message.getRuleCode(), projectCode, compId);
        } catch (Exception e) {
            log.error("Failed to save rule L2 cache for {}: {}", message.getRuleCode(), e.getMessage(), e);
        }
    }

    /**
     * 删除指定 ruleCode + compId 的 L2 快照
     *
     * @param projectCode 项目编码
     * @param ruleCode    规则编码
     * @param compId      作用域
     */
    public void removePublishedSnapshot(String projectCode, String ruleCode, String compId) {
        if (ruleCode == null || ruleCode.isEmpty()) {
            return;
        }
        try {
            String c = RuleCompIds.normalize(compId);
            stringRedisTemplate.delete(RuleRedisL2Keys.publishedRule(projectCode, ruleCode, c));
            stringRedisTemplate.opsForSet().remove(RuleRedisL2Keys.publishedScopesIndex(projectCode, ruleCode), c);
            log.debug("Rule L2 cache removed: {} projectCode={} compId={}", ruleCode, projectCode, c);
        } catch (Exception e) {
            log.error("Failed to remove rule L2 cache for {}: {}", ruleCode, e.getMessage(), e);
        }
    }

    /**
     * 删除某规则编码下所有作用域的 L2 快照及索引
     *
     * @param projectCode 项目编码
     * @param ruleCode    规则编码
     */
    public void removeAllPublishedSnapshots(String projectCode, String ruleCode) {
        if (ruleCode == null || ruleCode.isEmpty()) {
            return;
        }
        try {
            String idx = RuleRedisL2Keys.publishedScopesIndex(projectCode, ruleCode);
            Set<String> members = stringRedisTemplate.opsForSet().members(idx);
            if (members != null) {
                for (String m : members) {
                    stringRedisTemplate.delete(RuleRedisL2Keys.publishedRule(projectCode, ruleCode, m));
                }
            }
            stringRedisTemplate.delete(idx);
            log.debug("Rule L2 cache removed all scopes: {} projectCode={}", ruleCode, projectCode);
        } catch (Exception e) {
            log.error("Failed to remove all rule L2 cache for {}: {}", ruleCode, e.getMessage(), e);
        }
    }

    /**
     * 规则集发布成功后写入 Redis 快照（与单规则 L2 一致结构，payload 为 RulePushMessage）
     */
    public void savePublishedSetSnapshot(RulePushMessage message) {
        if (message == null || message.getSetCode() == null || message.getSetCode().isEmpty()) {
            return;
        }
        try {
            String projectCode = message.getProjectCode();
            String compId = RuleCompIds.normalize(message.getCompId());
            message.setCompId(compId);
            String key = RuleRedisL2Keys.publishedSet(projectCode, message.getSetCode(), compId);
            stringRedisTemplate.opsForValue().set(key, JSON.toJSONString(message));
            stringRedisTemplate.opsForSet().add(RuleRedisL2Keys.publishedSetScopesIndex(projectCode, message.getSetCode()), compId);
            log.debug("Rule set L2 cache saved: {} projectCode={} compId={}", message.getSetCode(), projectCode, compId);
        } catch (Exception e) {
            log.error("Failed to save rule set L2 cache for {}: {}", message.getSetCode(), e.getMessage(), e);
        }
    }

    public void removePublishedSetSnapshot(String projectCode, String setCode, String compId) {
        if (setCode == null || setCode.isEmpty()) {
            return;
        }
        try {
            String c = RuleCompIds.normalize(compId);
            stringRedisTemplate.delete(RuleRedisL2Keys.publishedSet(projectCode, setCode, c));
            stringRedisTemplate.opsForSet().remove(RuleRedisL2Keys.publishedSetScopesIndex(projectCode, setCode), c);
            log.debug("Rule set L2 cache removed: {} projectCode={} compId={}", setCode, projectCode, c);
        } catch (Exception e) {
            log.error("Failed to remove rule set L2 cache for {}: {}", setCode, e.getMessage(), e);
        }
    }

    /**
     * 删除某规则集编码下全部作用域的 L2 快照及索引
     *
     * @param projectCode 项目编码
     * @param setCode     规则集编码
     */
    public void removeAllPublishedSetSnapshots(String projectCode, String setCode) {
        if (setCode == null || setCode.isEmpty()) {
            return;
        }
        try {
            String idx = RuleRedisL2Keys.publishedSetScopesIndex(projectCode, setCode);
            Set<String> members = stringRedisTemplate.opsForSet().members(idx);
            if (members != null) {
                for (String m : members) {
                    stringRedisTemplate.delete(RuleRedisL2Keys.publishedSet(projectCode, setCode, m));
                }
            }
            stringRedisTemplate.delete(idx);
            log.debug("Rule set L2 cache removed all scopes: {} projectCode={}", setCode, projectCode);
        } catch (Exception e) {
            log.error("Failed to remove all rule set L2 cache for {}: {}", setCode, e.getMessage(), e);
        }
    }
}
