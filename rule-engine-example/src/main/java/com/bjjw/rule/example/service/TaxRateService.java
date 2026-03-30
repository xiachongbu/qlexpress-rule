package com.bjjw.rule.example.service;

import com.bjjw.rule.client.RuleEngineClient;
import com.bjjw.rule.example.dto.TaxRateQuery;
import com.bjjw.rule.model.dto.RuleResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Map;

/**
 * 决策表（Decision Table）调用示例
 *
 * 业务场景：客群×产品线定价表（与 data-example.sql 中 RC_PRICING_TABLE 一致）
 * 根据客群类型与产品条线，通过决策表匹配风险定价费率。
 *
 * 对应规则编码：RC_PRICING_TABLE
 * 决策表结构：
 *   条件列：taxpayerType（客群类型）, goodsCategory（产品条线）
 *   动作列：taxRate（风险定价费率）
 *   命中策略：FIRST（首次命中）
 */
@Slf4j
@Service
public class TaxRateService {

    private static final String RULE_CODE = "RC_PRICING_TABLE";

    @Resource
    private RuleEngineClient ruleClient;

    /**
     * 查询风险定价费率
     *
     * @param query 定价表查询参数 DTO
     * @return 风险定价费率，如 0.13、0.09、0.06、0.03
     */
    public BigDecimal queryTaxRate(TaxRateQuery query) {
        RuleResult result = ruleClient.execute(RULE_CODE, query);

        if (!result.isSuccess()) {
            log.error("决策表执行失败 [{}]: {}", RULE_CODE, result.getErrorMessage());
            throw new RuntimeException("定价表查询失败: " + result.getErrorMessage());
        }

        Object raw = result.getResult();
        Object value = unwrapDecisionOutput(raw, "taxRate");
        if (value == null) {
            log.warn("决策表未命中任何规则: {}", query);
            return null;
        }

        BigDecimal taxRate = new BigDecimal(value.toString());
        log.info("税率查询结果: {} => taxRate={}", query, taxRate);
        return taxRate;
    }

    /**
     * 决策表多动作时引擎返回 Map；单动作兼容仍为标量。优先取约定字段名，否则取 Map 中唯一一项。
     */
    private static Object unwrapDecisionOutput(Object raw, String preferredKey) {
        if (!(raw instanceof Map)) {
            return raw;
        }
        Map<?, ?> m = (Map<?, ?>) raw;
        if (preferredKey != null && m.containsKey(preferredKey)) {
            return m.get(preferredKey);
        }
        if (m.size() == 1) {
            return m.values().iterator().next();
        }
        return null;
    }
}
