package com.bjjw.rule.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("rule_definition_content")
public class RuleDefinitionContent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long definitionId;
    private String modelJson;
    private String compiledScript;
    private String compiledType;
    private Integer compileStatus;
    private String compileMessage;
    private LocalDateTime compileTime;
    /** 编辑模式：visual-可视化，script-脚本模式 */
    private String scriptMode;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
