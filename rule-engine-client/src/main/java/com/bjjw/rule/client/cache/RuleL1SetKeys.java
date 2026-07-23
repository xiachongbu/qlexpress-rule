package com.bjjw.rule.client.cache;

import com.bjjw.rule.model.constant.RuleCompIds;

/**
 * 规则集 L1 复合键，与单规则 {@link RuleL1CacheKeys} 隔离避免冲突
 */
public final class RuleL1SetKeys {

    private static final char SEP = '\u0002';
    private static final String PREFIX = "S";

    private RuleL1SetKeys() {
    }

    public static String key(String projectCode, String setCode, String scopeCompId) {
        return PREFIX + SEP + projectCode + SEP + setCode + SEP + RuleCompIds.normalize(scopeCompId);
    }

    public static boolean isKeyForSet(String cacheKey, String projectCode, String setCode) {
        if (cacheKey == null || setCode == null) {
            return false;
        }
        return cacheKey.startsWith(PREFIX + SEP + projectCode + SEP + setCode + SEP);
    }
}
