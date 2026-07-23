package com.bjjw.rule.model.dto.mgmt;

import lombok.Data;

/**
 * 按项目查询全部启用函数请求。
 */
@Data
public class RuleFunctionProjectAllRequest {
    /** 项目 ID */
    private Long projectId;
}
