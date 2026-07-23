package com.bjjw.rule.model.dto.mgmt;

import lombok.Data;

/**
 * 按省份下线规则请求。
 */
@Data
public class RuleDefinitionUnpublishRequest {
    /** 规则定义 ID */
    private Long definitionId;
    /** 作用域 compId，null 则下线所有作用域 */
    private String scopeCompId;
}
