package com.bjjw.rule.example.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 决策树查询参数 - RC_CREDIT_TREE 客户信用分层
 */
@Data
public class TaxpayerClassifyQuery {

    /** 年营收(万元) */
    private BigDecimal annualRevenue;

    /** 合规内控评分（0-100） */
    private int taxComplianceScore;

    /** 合作/经营年限 */
    private int yearsInBusiness;

    /** 是否存在严重违规 */
    private boolean hasViolation;
}
