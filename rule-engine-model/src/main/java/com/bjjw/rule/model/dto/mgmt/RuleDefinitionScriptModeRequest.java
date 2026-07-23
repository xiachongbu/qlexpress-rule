package com.bjjw.rule.model.dto.mgmt;

import lombok.Data;

/**
 * 更新编辑模式（visual / script）请求。
 */
@Data
public class RuleDefinitionScriptModeRequest {
    /** 模式：visual 或 script */
    private String scriptMode;
    /** 作用域 compId */
    private String scopeCompId;
}
