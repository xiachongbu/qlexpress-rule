package com.bjjw.rule.model.dto;

import lombok.Data;
import java.util.Map;

@Data
public class RuleExecuteRequest {
    private String ruleCode;
    private Map<String, Object> params;
    private boolean traceEnabled;
    /** 业务主键ID，用于关联具体业务记录 */
    private String businessId;
}
