package com.bjjw.rule.model.dto.mgmt;

import lombok.Data;

/**
 * 新增省份作用域空内容行请求。
 */
@Data
public class RuleDefinitionAddScopeRequest {
    /** 规则定义 ID */
    private Long definitionId;
    /** 作用域 compId */
    private String scopeCompId;
}
