package com.bjjw.rule.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("rule_definition")
public class RuleDefinition {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String ruleCode;
    private String ruleName;
    private String modelType;
    private String description;
    private Integer currentVersion;
    private Integer publishedVersion;
    private Integer status;
    private String createBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    private String updateBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
