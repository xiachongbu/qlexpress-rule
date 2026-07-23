package com.bjjw.rule.model.dto.mgmt;

import lombok.Data;

/**
 * 按字典类型查询启用项请求。
 */
@Data
public class RuleSysDictItemsRequest {
    /** 字典类型，如 COMP_SCOPE */
    private String type;
}
