package com.bjjw.rule.example.dto;

import lombok.Data;

/**
 * 复杂交叉表查询参数：多维场景定价矩阵（RC_MULTI_DIM_RATE）
 */
@Data
public class MultiDimRateQuery {

    /** 业务类型（基础通信/增值业务/宽带接入/ICT服务） */
    private String serviceType;

    /** 结算方式（预付费/后付费） */
    private String paymentMode;

    /** 客户类型（企业客户/个人客户/政企客户） */
    private String customerType;

    /** 纳税人资格（一般纳税人/小规模纳税人） */
    private String taxpayerQualification;
}
