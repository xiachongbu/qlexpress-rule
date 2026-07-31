package com.bjjw.rule.client.cache;

import lombok.Data;

@Data
public class CachedRule {
    private String ruleCode;
    /** 规则所属项目编码 */
    private String projectCode;
    /** 命中发布快照的作用域 compId（0 表示全国默认） */
    private String compId;
    private int version;
    private String modelType;
    private String compiledScript;
    private String compiledType;
    private String modelJson;
    /** 高精度计算开关，来自规则发布快照（rule_definition.precise_mode）；旧快照缺字段时为 false */
    private boolean precise;
    private long lastUpdateTime;
}
