package com.bjjw.rule.model.dto.mgmt;

import lombok.Data;

/**
 * 按省份删除规则内容请求。
 */
@Data
public class RuleDefinitionScopedDeleteRequest {
    /** 规则定义 ID */
    private Long definitionId;
    /** 作用域 compId */
    private String scopeCompId;
}
