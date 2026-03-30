package com.bjjw.rule.model.dto;

import lombok.Data;
import java.util.List;

@Data
public class RuleResult {
    private Object result;
    private List<Object> traces;
    private boolean success;
    private String errorMessage;
    private long executeTimeMs;
}
