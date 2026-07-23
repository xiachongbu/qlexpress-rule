package com.bjjw.rule.model.dto.mgmt;

import lombok.Data;

/**
 * 查询规则集成员列表（POST + JSON）
 */
@Data
public class RuleRuleSetMemberListRequest {
    /** 规则集 ID */
    private Long setId;
}
