package com.bjjw.rule.model.dto.mgmt;

import lombok.Data;

/**
 * 脚本模式直接保存脚本请求。
 */
@Data
public class RuleDefinitionScriptSaveRequest {
    /** 脚本正文 */
    private String script;
    /** 作用域 compId */
    private String scopeCompId;
}
