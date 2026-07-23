package com.bjjw.rule.model.dto.mgmt;

import lombok.Data;

/**
 * 删除省份作用域内容请求（scopeCompId 不可为全国 0）。
 */
@Data
public class RuleDefinitionRemoveScopeRequest {
    /** 规则定义 ID */
    private Long definitionId;
    /** 作用域 compId */
    private String scopeCompId;
}
