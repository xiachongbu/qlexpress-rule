package com.bjjw.rule.model.dto.mgmt;

import lombok.Data;

/**
 * 列出某规则下全部作用域内容行请求。
 */
@Data
public class RuleDefinitionContentListRequest {
    /** 规则定义 ID */
    private Long definitionId;
}
