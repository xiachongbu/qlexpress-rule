package com.bjjw.rule.model.dto.mgmt;

import lombok.Data;

/**
 * 从扁平 JSON 批量导入常量请求。
 */
@Data
public class RuleVariableImportConstantsJsonRequest {
    /** 项目 ID */
    private Long projectId;
    /** JSON 文本 */
    private String jsonContent;
}
