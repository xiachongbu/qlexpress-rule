package com.bjjw.rule.example.service;

import com.bjjw.rule.client.RuleEngineClient;
import com.bjjw.rule.example.dto.RateLookupQuery;
import com.bjjw.rule.model.dto.RuleResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;

/**
 * 交叉表（Cross Table）调用示例
 *
 * 业务场景：二维风险参数矩阵（与 data-example.sql 中 RC_RATE_MATRIX 一致）
 * 客群类型 × 产品条线 交叉矩阵输出定价系数。
 *
 * 对应规则编码：RC_RATE_MATRIX
 * 交叉表结构：
 *   行维度：taxpayerType（客群类型）
 *   列维度：goodsCategory（产品条线）
 *   结果值：taxRate（风险定价费率）
 *
 * 矩阵示例：
 *               | 货物   | 服务   | 不动产  | 无形资产
 *   一般纳税人   | 0.13  | 0.06  | 0.09   | 0.06
 *   小规模纳税人 | 0.03  | 0.03  | 0.05   | 0.03
 */
@Slf4j
@Service
public class RateLookupService {

    private static final String RULE_CODE = "RC_RATE_MATRIX";

    @Resource
    private RuleEngineClient ruleClient;

    /**
     * 在交叉矩阵中查找风险定价费率
     *
     * @param query 税率矩阵查询参数 DTO
     * @return 矩阵中对应的税率值
     */
    public BigDecimal lookupRate(RateLookupQuery query) {
        RuleResult result = ruleClient.execute(RULE_CODE, query);

        if (!result.isSuccess()) {
            log.error("交叉表执行失败 [{}]: {}", RULE_CODE, result.getErrorMessage());
            throw new RuntimeException("二维风险参数矩阵查询失败: " + result.getErrorMessage());
        }

        Object value = result.getResult();
        if (value == null) {
            log.warn("交叉表未找到匹配项: {}", query);
            return null;
        }

        BigDecimal rate = new BigDecimal(value.toString());
        log.info("税率矩阵查询结果: {} => rate={}", query, rate);
        return rate;
    }
}
