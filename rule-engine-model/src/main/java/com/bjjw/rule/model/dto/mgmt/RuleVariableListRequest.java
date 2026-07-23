package com.bjjw.rule.model.dto.mgmt;

import lombok.Data;

/**
 * 变量分页查询请求。
 */
@Data
public class RuleVariableListRequest {
    /** 页码 */
    private Integer pageNum;
    /** 每页条数 */
    private Integer pageSize;
    /** 项目 ID */
    private Long projectId;
    /** 变量类型 */
    private String varType;
    /** 关键词 */
    private String keyword;
    /** 仅独立变量（排除常量等） */
    private Boolean standaloneOnly;
    /** 变量来源，如 CONSTANT */
    private String varSource;
}
