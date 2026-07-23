package com.bjjw.rule.model.dto.mgmt;

import lombok.Data;

/**
 * 仅含项目 ID 的通用查询请求（如变量列表、树）。
 */
@Data
public class RuleVariableProjectIdRequest {
    /** 项目 ID */
    private Long projectId;
}
