package com.bjjw.rule.example.dto;

import lombok.Data;

/**
 * QL 脚本查询参数：商户黑名单查询脚本（RC_KYB_HTTP_SCRIPT）。
 * <p>脚本通过内置 httpPost 调用黑名单接口，读取本 DTO 转换后的上下文字段。</p>
 */
@Data
public class KybBlacklistScriptQuery {

    /** 商户编号 */
    private String merchantId;

    /** 法人身份证号（Mock 黑名单据此判定命中：含「9999」→命中涉诉风险） */
    private String legalPersonIdCard;

    /** 接口调用令牌，透传到 HTTP 请求头 Authorization */
    private String bizToken;
}
