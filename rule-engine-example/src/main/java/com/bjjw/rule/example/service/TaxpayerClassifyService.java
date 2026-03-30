package com.bjjw.rule.example.service;

import com.bjjw.rule.client.RuleEngineClient;
import com.bjjw.rule.example.dto.TaxpayerClassifyQuery;
import com.bjjw.rule.model.dto.RuleResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Map;

/**
 * 决策树（Decision Tree）调用示例
 *
 * 业务场景：客户信用分层（与 data-example.sql 中 RC_CREDIT_TREE 一致）
 * 结合负面记录、合规评分、营收规模、合作年限输出内部信用等级 A/B/C/D。
 *
 * 对应规则编码：RC_CREDIT_TREE
 * 决策树结构：
 *   输入变量：annualRevenue（年营收）, taxComplianceScore（合规内控评分）,
 *            yearsInBusiness（合作/经营年限）, hasViolation（是否存在严重违规）
 *   输出变量：creditLevel（内部信用等级 A/B/C/D）
 */
@Slf4j
@Service
public class TaxpayerClassifyService {

    private static final String RULE_CODE = "RC_CREDIT_TREE";

    @Resource
    private RuleEngineClient ruleClient;

    /**
     * 判定内部信用等级
     *
     * @param query 客户信用分层参数 DTO
     * @return 内部信用等级：A/B/C/D
     */
    public String classifyCreditLevel(TaxpayerClassifyQuery query) {
        RuleResult result = ruleClient.execute(RULE_CODE, query);

        if (!result.isSuccess()) {
            log.error("决策树执行失败 [{}]: {}", RULE_CODE, result.getErrorMessage());
            throw new RuntimeException("客户信用分层判定失败: " + result.getErrorMessage());
        }

        Object raw = result.getResult();
        String creditLevel = extractCreditLevel(raw);
        log.info("信用等级判定: {} => level={}", query, creditLevel);
        return creditLevel;
    }

    /**
     * 决策树多输出时为 Map，取 creditLevel；旧脚本仍为单值。
     */
    private static String extractCreditLevel(Object raw) {
        if (raw == null) {
            return "D";
        }
        if (raw instanceof Map) {
            Object v = ((Map<?, ?>) raw).get("creditLevel");
            return v != null ? v.toString() : "D";
        }
        return raw.toString();
    }
}
