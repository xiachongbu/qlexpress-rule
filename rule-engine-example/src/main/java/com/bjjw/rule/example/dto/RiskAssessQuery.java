package com.bjjw.rule.example.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 评分卡查询参数 - RC_RISK_SCORECARD 综合风险评分卡
 */
@Data
public class RiskAssessQuery {

    /** 内部信用等级（A/B/C/D） */
    private String creditLevel;

    /** 年营收(万元) */
    private BigDecimal annualRevenue;

    /** 合作/经营年限 */
    private int yearsInBusiness;

    /** 指标偏离度（如 0.15 表示 15%） */
    private BigDecimal taxBurdenDeviation;

    /** 历史风险事件次数 */
    private int violationCount;
}
