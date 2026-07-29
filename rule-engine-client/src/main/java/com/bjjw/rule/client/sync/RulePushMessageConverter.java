package com.bjjw.rule.client.sync;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.bjjw.rule.client.cache.CachedRule;
import com.bjjw.rule.client.cache.CachedRuleSet;
import com.bjjw.rule.model.constant.RuleCompIds;
import com.bjjw.rule.model.dto.RulePushMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * 推送消息与客户端内存缓存实体之间的转换
 */
public final class RulePushMessageConverter {

    private RulePushMessageConverter() {
    }

    /**
     * 将 PUBLISH 推送消息转为 {@link CachedRule}，供 L1 / L2 使用
     *
     * @param push 推送载荷
     * @return 缓存对象；ruleCode 为空时返回 null
     */
    public static CachedRule toCachedRule(RulePushMessage push) {
        if (push == null || push.getRuleCode() == null) {
            return null;
        }
        CachedRule cached = new CachedRule();
        cached.setRuleCode(push.getRuleCode());
        cached.setProjectCode(push.getProjectCode());
        cached.setCompId(RuleCompIds.normalize(push.getCompId()));
        cached.setVersion(push.getVersion() != null ? push.getVersion() : 0);
        cached.setModelType(push.getModelType());
        cached.setCompiledScript(push.getCompiledScript());
        cached.setCompiledType(push.getCompiledType());
        cached.setModelJson(push.getModelJson());
        cached.setLastUpdateTime(System.currentTimeMillis());
        return cached;
    }

    /**
     * SET_PUBLISH 推送转规则集缓存对象
     */
    public static CachedRuleSet toCachedRuleSet(RulePushMessage push) {
        if (push == null || push.getSetCode() == null || push.getSetCode().isEmpty()) {
            return null;
        }
        CachedRuleSet s = new CachedRuleSet();
        s.setSetCode(push.getSetCode());
        s.setProjectCode(push.getProjectCode());
        s.setCompId(RuleCompIds.normalize(push.getCompId()));
        s.setVersion(push.getVersion() != null ? push.getVersion() : 0);
        s.setMemberRuleCodes(parseMemberCodes(push.getMemberRuleCodes()));
        s.setHitPolicy(push.getHitPolicy());
        s.setLastUpdateTime(System.currentTimeMillis());
        return s;
    }

    private static List<String> parseMemberCodes(String json) {
        List<String> out = new ArrayList<>();
        if (json == null || json.isEmpty()) {
            return out;
        }
        try {
            JSONArray arr = JSON.parseArray(json);
            if (arr != null) {
                for (int i = 0; i < arr.size(); i++) {
                    out.add(arr.getString(i));
                }
            }
        } catch (Exception ignored) {
            // 非 JSON 数组时忽略
        }
        return out;
    }
}
