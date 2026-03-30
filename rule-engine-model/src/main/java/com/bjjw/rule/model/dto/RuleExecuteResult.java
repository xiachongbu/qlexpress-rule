package com.bjjw.rule.model.dto;

import lombok.Data;
import java.util.List;

@Data
public class RuleExecuteResult {
    private Object result;
    private boolean success;
    private String errorMessage;
    private Long executeTimeMs;
    private List<Object> traces;
}
