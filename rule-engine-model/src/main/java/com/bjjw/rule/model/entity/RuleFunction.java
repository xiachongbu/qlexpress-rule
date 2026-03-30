package com.bjjw.rule.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("rule_function")
public class RuleFunction {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String funcCode;
    private String funcName;
    private String description;
    private String paramsJson;
    private String returnType;
    private String implType;
    private String implScript;
    private String implClass;
    /** 方法名（JAVA/BEAN 类型时指定） */
    private String implMethod;
    /** Spring Bean 名称（BEAN 类型时指定） */
    private String implBeanName;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
