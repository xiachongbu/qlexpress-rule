package com.bjjw.rule.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("rule_published")
public class RulePublished {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String ruleCode;
    private Long definitionId;
    /** 规则所属项目编码 */
    private String projectCode;
    private Integer version;
    private String modelType;
    private String compiledScript;
    private String compiledType;
    private String modelJson;
    private Integer status;
    private String publishBy;
    private LocalDateTime publishTime;
    private LocalDateTime offlineTime;
}
