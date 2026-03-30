package com.bjjw.rule.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 数据对象字段的枚举选项（与 {@link com.bjjw.rule.model.entity.RuleVariableOption} 对应）。
 */
@Data
@TableName("rule_data_object_field_option")
public class RuleDataObjectFieldOption {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long fieldId;
    private String optionValue;
    private String optionLabel;
    private Integer sortOrder;
}
