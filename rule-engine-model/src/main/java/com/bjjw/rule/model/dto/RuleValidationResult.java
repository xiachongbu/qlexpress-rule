package com.bjjw.rule.model.dto;

import lombok.Data;

@Data
public class RuleValidationResult {
    private Long definitionId;
    private String ruleCode;
    private String ruleName;
    private String modelType;
    private boolean compileOk;
    private boolean executeOk;
    private String errorMsg;
}
