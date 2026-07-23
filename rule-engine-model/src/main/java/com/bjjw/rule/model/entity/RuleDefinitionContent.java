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
    /** 作用域省份：0 全国默认，其它为业务 compId */
    private String scopeCompId;
    private String modelJson;
    private String compiledScript;
    private String compiledType;
    private Integer compileStatus;
    private String compileMessage;
    private LocalDateTime compileTime;
    /** 编辑模式：visual-可视化，script-脚本模式 */
    private String scriptMode;

    /** 决策模型类型：TABLE/TREE/FLOW/CROSS/SCORE 等 */
    private String modelType;
    /** 当前设计版本号 */
    private Integer currentVersion;
    /** 已发布版本号 */
    private Integer publishedVersion;
    /** 状态：0-草稿，1-已发布，2-已下线 */
    private Integer status;
    /** 规则归属省份编码，0 表示通用/全国 */
    private String compId;
    /** 规则说明 */
    private String description;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
