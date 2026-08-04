package com.bjjw.rule.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 规则集定义（设计态）
 */
@Data
@TableName("rule_rule_set")
public class RuleRuleSet {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属项目 ID */
    private Long projectId;
    /** 规则集编码（全局唯一） */
    private String setCode;
    /** 规则集名称 */
    private String setName;
    private String description;
    /** 归属省份编码，0 表示通用/全国 */
    private String compId;
    /** 命中策略：ALL-全部执行，FIRST-首个命中即返回，UNIQUE-唯一命中 */
    private String hitPolicy;
    /** 日志上报：0-关，1-开；发布时随快照下发客户端 */
    private Integer reportLog;
    /** 状态：0-草稿，1-已发布，2-已下线 */
    private Integer status;
    /** 已发布版本号 */
    private Integer publishedVersion;

    /**
     * 分页列表：成员数量（非表字段，服务层填充）
     */
    @TableField(exist = false)
    private Integer memberCount;

    @TableField(fill = FieldFill.INSERT)
    private String createBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
