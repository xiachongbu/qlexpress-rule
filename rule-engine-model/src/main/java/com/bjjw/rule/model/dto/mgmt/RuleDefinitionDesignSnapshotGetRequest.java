package com.bjjw.rule.model.dto.mgmt;

import lombok.Data;

/**
 * 获取单条设计快照 modelJson 请求。
 */
@Data
public class RuleDefinitionDesignSnapshotGetRequest {
    /** 快照主键 ID */
    private Long id;
    /** 规则定义 ID（校验归属） */
    private Long definitionId;
    /** 作用域 compId */
    private String scopeCompId;
}
