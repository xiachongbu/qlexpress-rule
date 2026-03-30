package com.bjjw.rule.client.cache;

import lombok.Data;

@Data
public class CachedRule {
    private String ruleCode;
    /** 规则所属项目编码 */
    private String projectCode;
    private int version;
    private String modelType;
    private String compiledScript;
    private String compiledType;
    private String modelJson;
    private long lastUpdateTime;
}
