package com.bjjw.rule.model.dto.mgmt;

import lombok.Data;

import java.util.Map;

/**
 * 管理端规则试跑请求。
 */
@Data
public class RuleDefinitionTestExecuteRequest {
    /** 规则定义 ID */
    private Long definitionId;
    /** 作用域 compId */
    private String scopeCompId;
    /** 试跑入参 */
    private Map<String, Object> params;
    /** 业务主键ID，用于关联具体业务记录 */
    private String businessId;
}
