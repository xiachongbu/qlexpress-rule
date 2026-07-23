package com.bjjw.rule.model.dto.mgmt;

import lombok.Data;

/**
 * 执行日志分页查询请求；时间格式 yyyy-MM-dd HH:mm:ss。
 */
@Data
public class RuleExecutionLogListRequest {
    /** 页码 */
    private Integer pageNum;
    /** 每页条数 */
    private Integer pageSize;
    /** 规则编码 */
    private String ruleCode;
    /** 项目编码 */
    private String projectCode;
    /** 来源 SERVER / CLIENT */
    private String source;
    /** 业务主键ID */
    private String businessId;
    /** 开始时间（含） */
    private String startTime;
    /** 结束时间（含） */
    private String endTime;
}
