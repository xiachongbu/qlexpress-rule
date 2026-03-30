package com.bjjw.rule.example.service;

import com.bjjw.rule.client.RuleEngineClient;
import com.bjjw.rule.example.dto.InvoiceFraudScoreQuery;
import com.bjjw.rule.model.dto.RuleResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * 复杂评分卡调用示例：交易票据异常评分（分组维度加权）。
 *
 * <p>对应规则编码：{@code RC_INVOICE_FRAUD_SCORE}</p>
 */
@Slf4j
@Service
public class InvoiceFraudScoreService {

    private static final String RULE_CODE = "RC_INVOICE_FRAUD_SCORE";

    @Resource
    private RuleEngineClient ruleClient;

    /**
     * 执行票据与交易相关指标评分，返回分数及分档（由规则脚本决定字段名）。
     *
     * @param query 评分入参
     * @return 评分结果 Map
     */
    public Map<String, Object> assessRisk(InvoiceFraudScoreQuery query) {
        RuleResult result = ruleClient.execute(RULE_CODE, query);

        if (!result.isSuccess()) {
            log.error("复杂评分卡执行失败 [{}]: {}", RULE_CODE, result.getErrorMessage());
            throw new RuntimeException("票据风险评分失败: " + result.getErrorMessage());
        }

        Map<String, Object> assessment = new HashMap<>();
        Object raw = result.getResult();
        if (raw instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> scoreMap = (Map<String, Object>) raw;
            assessment.putAll(scoreMap);
        } else {
            assessment.put("invoiceRiskScore", raw);
            if (raw != null) {
                double totalScore = Double.parseDouble(raw.toString());
                String riskLevel;
                if (totalScore >= 80) {
                    riskLevel = "低风险";
                } else if (totalScore >= 60) {
                    riskLevel = "中风险";
                } else {
                    riskLevel = "高风险";
                }
                assessment.put("invoiceRiskLevel", riskLevel);
            }
        }
        assessment.put("success", true);
        assessment.put("executeTimeMs", result.getExecuteTimeMs());

        log.info("票据风险评分完成: {} => {}", query, assessment);
        return assessment;
    }
}
