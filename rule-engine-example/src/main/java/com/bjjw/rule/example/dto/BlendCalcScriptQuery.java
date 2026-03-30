package com.bjjw.rule.example.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * QL 脚本查询参数：混业组合计费（RC_BLEND_CALC_SCRIPT）
 */
@Data
public class BlendCalcScriptQuery {

    /** 纳税人资格（一般纳税人/小规模纳税人） */
    private String taxpayerQualification;

    /** 含税账单金额（元） */
    private BigDecimal billingAmount;

    /** 低费率产品线收入占比（如 0.6 表示 60%） */
    private BigDecimal basicServiceRatio;

    /** 高费率产品线收入占比（如 0.4 表示 40%） */
    private BigDecimal vasServiceRatio;
}
