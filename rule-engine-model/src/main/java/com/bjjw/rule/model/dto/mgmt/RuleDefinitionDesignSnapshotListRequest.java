package com.bjjw.rule.model.dto.mgmt;

import lombok.Data;

/**
 * 设计保存快照分页列表查询请求（不含 modelJson）。
 */
@Data
public class RuleDefinitionDesignSnapshotListRequest {
    /** 规则定义 ID */
    private Long definitionId;
    /** 作用域 compId */
    private String scopeCompId;
    /** 页码 */
    private Integer pageNum;
    /** 每页条数 */
    private Integer pageSize;
}
