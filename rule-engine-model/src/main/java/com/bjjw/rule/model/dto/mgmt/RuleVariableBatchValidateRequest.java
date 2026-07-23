package com.bjjw.rule.model.dto.mgmt;

import lombok.Data;

/**
 * 项目维度批量校验规则请求。
 */
@Data
public class RuleVariableBatchValidateRequest {
    /** 项目 ID */
    private Long projectId;
}
