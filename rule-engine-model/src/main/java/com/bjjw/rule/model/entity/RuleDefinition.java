package com.bjjw.rule.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("rule_definition")
public class RuleDefinition {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String ruleCode;
    private String ruleName;
    private String description;

    /**
     * 创建规则时的模型类型（仅请求体使用，非表字段）。
     */
    @TableField(exist = false)
    private String modelType;

    /**
     * 创建规则时首选作用域（仅请求体使用，非表字段）；与 rule_definition_content.scope_comp_id 一致。
     */
    @TableField(exist = false)
    private String initialScopeCompId;

    /**
     * 列表查询时携带各省份内容摘要，由服务层填充。
     */
    @TableField(exist = false)
    private List<RuleDefinitionContent> contentSummaries;

    private String createBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    private String updateBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
