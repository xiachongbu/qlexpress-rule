package com.bjjw.rule.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 规则集成员及执行顺序
 */
@Data
@TableName("rule_rule_set_member")
public class RuleRuleSetMember {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 规则集 ID */
    private Long setId;
    /** 成员规则定义 ID */
    private Long definitionId;
    /** 执行顺序，升序 */
    private Integer sortOrder;
}
