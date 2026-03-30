package com.bjjw.rule.example.service;

import com.bjjw.rule.client.RuleEngineClient;
import com.bjjw.rule.example.dto.TaxRateByCtxQuery;
import com.bjjw.rule.model.dto.RuleResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;

/**
 * 对象传入（Object Context）调用示例
 *
 * 业务场景：对象传入定价（与 data-example.sql 中 RC_PRICING_BY_OBJECT 一致）
 * 入参 DTO 映射为上下文字段 taxpayerType、goodsCategory，与规则脚本一致。
 *
 * 对应规则编码：RC_PRICING_BY_OBJECT
 */
@Slf4j
@Service
public class TaxRateByCtxService {

    private static final String RULE_CODE = "RC_PRICING_BY_OBJECT";

    @Resource
    private RuleEngineClient ruleClient;

    /**
     * 对象入参查询风险定价费率
     *
     * @param query 客群类型、产品条线等查询参数
     * @return 风险定价费率
     */
    public Object queryTaxRate(TaxRateByCtxQuery query) {
        RuleResult result = ruleClient.execute(RULE_CODE, query);

        if (!result.isSuccess()) {
            log.error("对象传入规则执行失败 [{}]: {}", RULE_CODE, result.getErrorMessage());
            throw new RuntimeException("对象传入定价失败: " + result.getErrorMessage());
        }

        Object value = result.getResult();
        if (value == null) {
            log.warn("对象传入规则未命中: {}", query);
            return null;
        }
        return value;
    }
}
