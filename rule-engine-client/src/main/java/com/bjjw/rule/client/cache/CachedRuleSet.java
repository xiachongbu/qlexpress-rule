package com.bjjw.rule.client.cache;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端内存/Redis 中的已发布规则集快照
 */
@Data
public class CachedRuleSet {
    private String setCode;
    private String projectCode;
    /** 解析命中的作用域 compId */
    private String compId;
    private int version;
    /** 成员 rule_code 执行顺序 */
    private List<String> memberRuleCodes = new ArrayList<>();
    /** 命中策略：ALL/FIRST/UNIQUE，空视为 ALL（兼容旧版快照） */
    private String hitPolicy;
    /** 日志上报：0-关，1-开，来自规则集发布快照（rule_rule_set.report_log） */
    private int reportLog;
    private long lastUpdateTime;
}
