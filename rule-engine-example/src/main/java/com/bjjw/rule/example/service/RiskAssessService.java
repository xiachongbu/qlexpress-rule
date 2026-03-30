package com.bjjw.rule.example.service;

import com.bjjw.rule.client.RuleEngineClient;
import com.bjjw.rule.example.dto.RiskAssessQuery;
import com.bjjw.rule.model.dto.RuleResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * 评分卡（Scorecard）调用示例
 *
 * 业务场景：综合风险评分卡（与 data-example.sql 中 RC_RISK_SCORECARD 一致）
 * 基于信用等级、营收、年限、指标偏离、历史事件的加权评分与风险档。
 *
 * 对应规则编码：RC_RISK_SCORECARD
 * 评分卡结构：
 *   初始分数：100
 *   评分项：
 *     1. 内部信用等级 (creditLevel)    - 权重 30%
 *     2. 年营收规模 (annualRevenue)     - 权重 20%
 *     3. 合作/经营年限 (yearsInBusiness)     - 权重 15%
 *     4. 指标偏离度 (taxBurdenDeviation) - 权重 20%
 *     5. 历史风险事件次数 (violationCount)   - 权重 15%
 *   等级阈值：
 *     低风险: >= 80 分
 *     中风险: 60-79 分
 *     高风险: < 60 分
 */
@Slf4j
@Service
public class RiskAssessService {

    private static final String RULE_CODE = "RC_RISK_SCORECARD";

    @Resource
    private RuleEngineClient ruleClient;

    /**
     * 执行综合风险评分与分档
     *
     * @param query 综合风险评分卡参数 DTO
     * @return 评分结果，包含总分和风险等级
     */
    public Map<String, Object> assessRisk(RiskAssessQuery query) {
        RuleResult result = ruleClient.execute(RULE_CODE, query);

        if (!result.isSuccess()) {
            log.error("评分卡执行失败 [{}]: {}", RULE_CODE, result.getErrorMessage());
            throw new RuntimeException("综合风险评分失败: " + result.getErrorMessage());
        }

        Map<String, Object> assessment = new HashMap<>();
        // 评分卡配置了阈值时，引擎返回 { 分数变量, 档位变量 } 的 Map；未配置阈值时为纯分数
        Object raw = result.getResult();
        if (raw instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> scoreMap = (Map<String, Object>) raw;
            assessment.putAll(scoreMap);
        } else {
            assessment.put("totalScore", raw);
        }
        assessment.put("success", true);
        assessment.put("executeTimeMs", result.getExecuteTimeMs());

        log.info("综合风险评分完成: {} => {}", query, assessment);
        return assessment;
    }
}
