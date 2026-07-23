package com.bjjw.rule.model.dto.mgmt;

import lombok.Data;

import java.util.List;

/**
 * 全量保存规则集成员顺序（POST + JSON）
 */
@Data
public class RuleRuleSetMembersSaveRequest {
    /** 规则集 ID */
    private Long setId;
    /** 成员规则定义 ID，按执行顺序排列 */
    private List<Long> definitionIdsInOrder;
}
