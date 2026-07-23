package com.bjjw.rule.model.dto.mgmt;

import lombok.Data;

/**
 * 从 JSON 导入数据对象请求。
 */
@Data
public class RuleDataObjectImportJsonRequest {
    /** 项目 ID */
    private Long projectId;
    /** 对象类型 */
    private String objectType;
    /** 对象编码 */
    private String objectCode;
    /** JSON 文本 */
    private String jsonContent;
}
