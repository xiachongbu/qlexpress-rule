package com.bjjw.rule.example.dto;

import lombok.Data;

/**
 * 决策表查询参数 - RC_PRICING_TABLE 客群×产品线定价表
 */
@Data
public class TaxRateQuery {

    /** 客群类型（rule_variable.var_label），如「一般纳税人」「小规模纳税人」 */
    private String taxpayerType;

    /** 产品条线，如「货物」「服务」「不动产」「无形资产」 */
    private String goodsCategory;
}
