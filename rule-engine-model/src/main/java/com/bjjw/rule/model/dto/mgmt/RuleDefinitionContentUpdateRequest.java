package com.bjjw.rule.model.dto.mgmt;

import lombok.Data;

/**
 * 修改规则内容的省份归属和说明。
 */
@Data
public class RuleDefinitionContentUpdateRequest {
    /** 规则定义 ID */
    private Long definitionId;
    /** 当前作用域 compId（定位用） */
    private String scopeCompId;
    /** 新省份编码 */
    private String compId;
    /** 新说明 */
    private String description;
}
