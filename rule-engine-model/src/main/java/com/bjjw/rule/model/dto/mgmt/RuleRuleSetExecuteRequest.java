package com.bjjw.rule.model.dto.mgmt;

import lombok.Data;

import java.util.Map;

/**
 * 规则集试跑请求（POST + JSON）
 */
@Data
public class RuleRuleSetExecuteRequest {
    /** 规则集 ID（与 setCode 二选一） */
    private Long setId;
    /** 规则集编码 */
    private String setCode;
    /** 作用域 compId，默认 0 */
    private String scopeCompId;
    /** 入参上下文 */
    private Map<String, Object> params;
    /** 业务主键ID，用于关联具体业务记录 */
    private String businessId;
}
