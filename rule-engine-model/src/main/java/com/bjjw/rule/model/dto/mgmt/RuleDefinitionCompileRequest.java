package com.bjjw.rule.model.dto.mgmt;

import lombok.Data;

/**
 * 编译指定作用域请求（路径参数含 definitionId）。
 */
@Data
public class RuleDefinitionCompileRequest {
    /** 作用域 compId，可空则默认全国 */
    private String scopeCompId;
}
