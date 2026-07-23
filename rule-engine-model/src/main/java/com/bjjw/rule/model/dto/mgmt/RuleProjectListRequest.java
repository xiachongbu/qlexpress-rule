package com.bjjw.rule.model.dto.mgmt;

import lombok.Data;

/**
 * 项目分页查询请求。
 */
@Data
public class RuleProjectListRequest {
    /** 页码 */
    private Integer pageNum;
    /** 每页条数 */
    private Integer pageSize;
    /** 关键词 */
    private String keyword;
}
