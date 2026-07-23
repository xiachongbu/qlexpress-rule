package com.bjjw.rule.model.constant;

/**
 * 规则引擎在 Redis 中的 L2 缓存键约定（服务端写入、客户端读取需保持一致）
 */
public final class RuleRedisL2Keys {

    private RuleRedisL2Keys() {
    }

    /**
     * 记录某 ruleCode 下已写入 Redis 的 compId 集合，便于下线时批量删除 L2 键
     *
     * @param projectCode 项目编码
     * @param ruleCode    规则编码
     * @return Redis SET 键
     */
    public static String publishedScopesIndex(String projectCode, String ruleCode) {
        return "rule:published:scopes:" + projectCode + ":" + ruleCode;
    }

    /**
     * 已发布规则快照键，值为 {@link com.bjjw.rule.model.dto.RulePushMessage} 的 JSON（action=PUBLISH）
     *
     * @param projectCode 项目编码
     * @param ruleCode    规则编码
     * @param compId      作用域，全国为 {@link RuleCompIds#NATIONAL}
     * @return Redis key
     */
    public static String publishedRule(String projectCode, String ruleCode, String compId) {
        return "rule:published:" + projectCode + ":" + ruleCode + ":" + RuleCompIds.normalize(compId);
    }

    /**
     * 记录某 setCode 下已写入 Redis 的规则集作用域集合，便于下线时批量删除
     *
     * @param projectCode 项目编码
     * @param setCode     规则集编码
     */
    public static String publishedSetScopesIndex(String projectCode, String setCode) {
        return "rule:published:set:scopes:" + projectCode + ":" + setCode;
    }

    /**
     * 已发布规则集快照键，值为 {@link com.bjjw.rule.model.dto.RulePushMessage} 的 JSON（action=SET_PUBLISH）
     *
     * @param projectCode 项目编码
     * @param setCode     规则集编码
     * @param compId      作用域
     */
    public static String publishedSet(String projectCode, String setCode, String compId) {
        return "rule:published:set:" + projectCode + ":" + setCode + ":" + RuleCompIds.normalize(compId);
    }
}
