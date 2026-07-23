package com.bjjw.rule.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("rule_execution_log")
public class RuleExecutionLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String ruleCode;
    private String projectCode;
    private Integer ruleVersion;
    private String modelType;
    private String source;
    /** 执行时的省份编码 */
    private String compId;
    private String clientAppName;
    private String clientIp;
    /** 业务主键ID，用于关联具体业务记录 */
    private String businessId;
    private String inputParams;
    private String outputResult;
    private String traceInfo;
    private Integer success;
    private String errorMessage;
    private Long executeTimeMs;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
