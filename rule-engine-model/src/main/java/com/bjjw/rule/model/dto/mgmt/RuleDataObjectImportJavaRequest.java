package com.bjjw.rule.model.dto.mgmt;

import lombok.Data;

/**
 * 从 Java 源码导入数据对象请求。
 */
@Data
public class RuleDataObjectImportJavaRequest {
    /** 项目 ID */
    private Long projectId;
    /** INPUT / OUTPUT 等 */
    private String objectType;
    /** Java 源码 */
    private String javaSource;
}
