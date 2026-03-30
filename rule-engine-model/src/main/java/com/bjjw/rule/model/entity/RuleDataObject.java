package com.bjjw.rule.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("rule_data_object")
public class RuleDataObject {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String objectCode;
    private String objectLabel;
    /** 脚本中的对象引用名（默认驼峰，如 taxRequest） */
    private String scriptName;
    private String objectType;
    private String sourceType;
    private String sourceContent;
    private Long parentObjectId;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
