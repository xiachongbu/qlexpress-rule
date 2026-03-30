package com.bjjw.rule.example.dto;

import lombok.Data;

/**
 * 交叉表查询参数 - RC_RATE_MATRIX 二维风险参数矩阵
 */
@Data
public class RateLookupQuery {

    /** 行维度：客群类型 */
    private String taxpayerType;

    /** 列维度：产品条线 */
    private String goodsCategory;
}
