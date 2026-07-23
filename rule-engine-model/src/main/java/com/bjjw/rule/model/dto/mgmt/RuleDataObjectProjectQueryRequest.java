package com.bjjw.rule.model.dto.mgmt;

import lombok.Data;

/**
 * 按项目查询数据对象列表请求。
 */
@Data
public class RuleDataObjectProjectQueryRequest {
    /** 项目 ID */
    private Long projectId;
}
