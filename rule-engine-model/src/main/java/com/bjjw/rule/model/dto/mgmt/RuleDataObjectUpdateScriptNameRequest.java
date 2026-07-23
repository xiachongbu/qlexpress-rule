package com.bjjw.rule.model.dto.mgmt;

import lombok.Data;

/**
 * 更新数据对象脚本引用名请求。
 */
@Data
public class RuleDataObjectUpdateScriptNameRequest {
    /** 脚本中引用名 */
    private String scriptName;
}
