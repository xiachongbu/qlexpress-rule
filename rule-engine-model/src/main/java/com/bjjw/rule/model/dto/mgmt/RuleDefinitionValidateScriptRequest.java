package com.bjjw.rule.model.dto.mgmt;

import lombok.Data;

/**
 * 校验脚本语法请求。
 */
@Data
public class RuleDefinitionValidateScriptRequest {
    /** 脚本正文 */
    private String script;
}
