package com.bjjw.rule.model.dto;

import lombok.Data;
import java.util.Map;

@Data
public class RuleExecuteRequest {
    private String ruleCode;
    private Map<String, Object> params;
    private boolean traceEnabled;
}
