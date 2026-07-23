package com.bjjw.rule.model.dto.mgmt;

import lombok.Data;

/**
 * 规则集分页查询请求（POST + JSON）
 */
@Data
public class RuleRuleSetListRequest {
    /** 页码，从 1 开始 */
    private Integer pageNum;
    /** 每页条数 */
    private Integer pageSize;
    /** 项目 ID */
    private Long projectId;
    /** 关键词（名称/编码） */
    private String keyword;
}
