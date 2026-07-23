package com.bjjw.rule.model.dto.mgmt;

import lombok.Data;

/**
 * 从 DDL 导入数据对象请求。
 */
@Data
public class RuleDataObjectImportDdlRequest {
    /** 项目 ID */
    private Long projectId;
    /** 对象类型 */
    private String objectType;
    /** DDL 文本 */
    private String ddlSource;
}
