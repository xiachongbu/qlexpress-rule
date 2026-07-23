package com.bjjw.rule.model.dto.mgmt;

import lombok.Data;

/**
 * 查询单作用域设计内容请求。
 */
@Data
public class RuleDefinitionContentQueryRequest {
    /** 规则定义 ID */
    private Long definitionId;
    /** 作用域 compId，默认全国 0 */
    private String scopeCompId;
}
