package com.bjjw.rule.example.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 复杂评分卡查询参数：交易票据异常评分（RC_INVOICE_FRAUD_SCORE）
 */
@Data
public class InvoiceFraudScoreQuery {

    /** 客户等级（钻石/金/银/铜） */
    private String customerLevel;

    /** 月消费金额（元） */
    private BigDecimal monthlyConsumption;

    /** 开票偏差率（如 0.05 表示 5%） */
    private BigDecimal invoiceDeviationRate;

    /** 红冲发票比例（如 0.02 表示 2%） */
    private BigDecimal redInvoiceRatio;

    /** 零税率发票占比 */
    private BigDecimal zeroRateInvoiceRatio;

    /** 跨地区开票比例 */
    private BigDecimal crossRegionInvoiceRatio;
}
