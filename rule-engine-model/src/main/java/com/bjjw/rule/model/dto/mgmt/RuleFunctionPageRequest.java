package com.bjjw.rule.model.dto.mgmt;

import lombok.Data;

/**
 * 按项目分页查询函数列表请求（路径参数含 projectId）。
 */
@Data
public class RuleFunctionPageRequest {
    /** 页码 */
    private Integer pageNum;
    /** 每页条数 */
    private Integer pageSize;
}
