package com.bjjw.rule.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("rule_project")
public class RuleProject {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String projectCode;
    private String projectName;
    private String description;
    private Integer status;
    
    /**
     * 访问Token
     */
    private String accessToken;
    
    private String createBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    private String updateBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
