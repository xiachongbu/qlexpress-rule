package com.bjjw.rule.model.dto.mgmt;

import lombok.Data;

/**
 * 更新数据对象类型请求。
 */
@Data
public class RuleDataObjectUpdateTypeRequest {
    /** INPUT / OUTPUT 等 */
    private String objectType;
}
