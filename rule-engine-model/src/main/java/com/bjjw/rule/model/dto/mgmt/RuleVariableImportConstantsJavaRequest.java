package com.bjjw.rule.model.dto.mgmt;

import lombok.Data;

/**
 * 从 Java 常量类批量导入常量请求。
 */
@Data
public class RuleVariableImportConstantsJavaRequest {
    /** 项目 ID */
    private Long projectId;
    /** Java 源码 */
    private String javaSource;
}
