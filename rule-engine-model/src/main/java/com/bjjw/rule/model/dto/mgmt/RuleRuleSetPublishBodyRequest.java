package com.bjjw.rule.model.dto.mgmt;

import lombok.Data;

import java.util.List;

/**
 * 规则集发布请求体（与单规则发布作用域选择一致）
 */
@Data
public class RuleRuleSetPublishBodyRequest {
    private String changeLog;
    /** 要发布的作用域 compId；null 表示自动取「全部成员均已上线」的作用域交集 */
    private List<String> scopeCompIds;
}
