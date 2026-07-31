package com.bjjw.rule.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
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
    /** 作用域省份：0 全国默认，其它为业务 compId */
    private String compId;
    private Integer version;
    private String modelType;
    private String compiledScript;
    private String compiledType;
    private String modelJson;
    /** 高精度计算：0-关闭，1-开启（BigDecimal），发布时取自 rule_definition.precise_mode */
    private Integer preciseMode;
    private Integer status;
    private String publishBy;
    private LocalDateTime publishTime;
    private LocalDateTime offlineTime;
}
