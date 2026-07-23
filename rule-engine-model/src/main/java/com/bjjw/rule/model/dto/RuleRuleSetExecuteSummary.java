package com.bjjw.rule.model.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 规则集链式执行汇总结果（管控试跑 / 可与客户端结构对齐）
 */
@Data
public class RuleRuleSetExecuteSummary {
    private boolean success;
    private String errorMessage;
    /** 各步规则编码的执行结果摘要 */
    private List<RuleRuleSetStepResult> steps = new ArrayList<>();
    /** 最终上下文中的 result 摘要（JSON 友好对象） */
    private Object finalResult;

    @Data
    public static class RuleRuleSetStepResult {
        private String ruleCode;
        private boolean success;
        private Object result;
        private String errorMessage;
        private long executeTimeMs;
    }
}
