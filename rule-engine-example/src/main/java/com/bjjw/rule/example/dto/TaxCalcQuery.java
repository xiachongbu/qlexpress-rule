package com.bjjw.rule.example.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 决策流查询参数 - RC_EXPOSURE_FLOW 敞口与费用试算流程
 */
@Data
public class TaxCalcQuery {

    /** 客群类型 */
    private String taxpayerType;

    /** 交易金额(含税口径) */
    private BigDecimal totalAmount;

    /** 产品条线 */
    private String goodsCategory;

    /** 是否适用减免政策 */
    private boolean isExempt;
}
