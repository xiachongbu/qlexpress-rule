package com.bjjw.rule.client.cache;

import com.bjjw.rule.model.constant.RuleCompIds;

/**
 * L1 缓存复合键：同一 ruleCode 在不同作用域 compId 下对应不同快照，须与解析请求时的 compId 一致。
 */
public final class RuleL1CacheKeys {

    private static final char SEP = '\u0001';

    private RuleL1CacheKeys() {
    }

    /**
     * 生成 L1 条目的复合键
     *
     * @param projectCode   项目编码
     * @param ruleCode      规则编码
     * @param scopeCompId   解析请求作用域（与服务端 sync 接口 compId 一致，先省后全国逻辑在 L2/HTTP 层完成）
     * @return 永不为 null
     */
    public static String key(String projectCode, String ruleCode, String scopeCompId) {
        return projectCode + SEP + ruleCode + SEP + RuleCompIds.normalize(scopeCompId);
    }

    /**
     * 判断是否属于某 ruleCode 下的缓存条目（用于推送失效时按规则批量删除）
     */
    public static boolean isKeyForRule(String cacheKey, String projectCode, String ruleCode) {
        if (cacheKey == null || ruleCode == null) {
            return false;
        }
        return cacheKey.startsWith(projectCode + SEP + ruleCode + SEP);
    }
}
