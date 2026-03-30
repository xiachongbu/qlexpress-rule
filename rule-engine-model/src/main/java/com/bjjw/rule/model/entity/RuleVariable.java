package com.bjjw.rule.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("rule_variable")
public class RuleVariable {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String varCode;
    private String varLabel;
    /** 脚本中的变量名（默认驼峰，如 totalAmount） */
    private String scriptName;
    private String varType;
    private String varSource;
    private String defaultValue;
    private String valueRange;
    private String exampleValue;
    private String description;
    private Integer sortOrder;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
