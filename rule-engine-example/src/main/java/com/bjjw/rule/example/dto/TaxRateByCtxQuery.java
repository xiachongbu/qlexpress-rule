package com.bjjw.rule.example.dto;

import lombok.Data;

import java.util.Map;

/**
 * 对象传入示例 - 税率判定查询参数
 * <p>
 * 与 RC_PRICING_BY_OBJECT 规则变量对齐的平铺入参（DTO 字段即上下文字段名）。
 */
@Data
public class TaxRateByCtxQuery {

    /** 客群类型（与 rule_variable 中 taxpayerType 一致） */
    private String taxpayerType;

    /** 产品条线 */
    private String goodsCategory;
}
