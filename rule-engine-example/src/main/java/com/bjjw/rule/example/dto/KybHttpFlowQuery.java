package com.bjjw.rule.example.dto;

import lombok.Data;

/**
 * 决策流查询参数：商户进件工商核验流程（RC_KYB_HTTP_FLOW）。
 * <p>字段与规则变量一致，SDK 将其转为 Map 注入规则上下文；HTTP 动作节点会读取这些字段。</p>
 */
@Data
public class KybHttpFlowQuery {

    /** 商户编号 */
    private String merchantId;

    /** 商户名称（Mock 工商核验据此判定风险等级：含「风险」→HIGH，含「关注」→MEDIUM，否则 LOW） */
    private String merchantName;

    /** 接口调用令牌，透传到 HTTP 请求头 Authorization */
    private String bizToken;
}
