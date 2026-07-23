package com.bjjw.rule.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 规则设计保存快照（按作用域记录每次带说明的保存，与发布版本历史表独立）。
 */
@Data
@TableName("rule_definition_design_snapshot")
public class RuleDefinitionDesignSnapshot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long definitionId;
    private String scopeCompId;
    private String modelJson;
    private String changeLog;
    private Integer designVersion;
    private String createBy;
    private LocalDateTime createTime;
}
