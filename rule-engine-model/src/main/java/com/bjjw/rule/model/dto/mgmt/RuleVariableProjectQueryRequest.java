package com.bjjw.rule.model.dto.mgmt;

import lombok.Data;

/**
 * 按项目查询变量列表请求。
 */
@Data
public class RuleVariableProjectQueryRequest {
    /** 项目 ID */
    private Long projectId;
}
