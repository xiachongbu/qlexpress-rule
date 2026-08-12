package com.bjjw.rule.example.service;

import com.bjjw.rule.client.RuleEngineClient;
import com.bjjw.rule.example.dto.KybHttpFlowQuery;
import com.bjjw.rule.model.dto.RuleResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * 决策流 HTTP 调用示例：商户进件工商核验流程。
 *
 * <p>对应规则编码：{@code RC_KYB_HTTP_FLOW}。决策流的 task 动作节点通过内置 {@code httpCall}
 * 调用工商核验接口，随后 decision 节点依据返回的风险等级决定「自动准入」或「转人工审核」。</p>
 *
 * <p>注意：HTTP 调用在规则执行线程内同步发起，函数已内置连接/读取超时（见规则 model_json
 * 中的 connectTimeout/readTimeout），避免慢接口拖垮执行线程。</p>
 */
@Slf4j
@Service
public class KybHttpFlowService {

    private static final String RULE_CODE = "RC_KYB_HTTP_FLOW";

    @Resource
    private RuleEngineClient ruleClient;

    /**
     * 执行商户进件工商核验决策流。
     *
     * @param query 进件参数（商户编号/名称/令牌）
     * @return 含 kybRiskLevel、auditResult、auditRemark 等输出的包装 Map
     */
    public Map<String, Object> verifyAndAudit(KybHttpFlowQuery query) {
        RuleResult result = ruleClient.execute(RULE_CODE, query, null);

        if (!result.isSuccess()) {
            log.error("KYB 决策流执行失败 [{}]: {}", RULE_CODE, result.getErrorMessage());
            throw new RuntimeException("商户进件工商核验流程执行失败: " + result.getErrorMessage());
        }

        Map<String, Object> output = new HashMap<>();
        Object raw = result.getResult();
        if (raw instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> flowOut = (Map<String, Object>) raw;
            output.putAll(flowOut);
        } else {
            output.put("result", raw);
        }
        output.put("success", true);
        output.put("executeTimeMs", result.getExecuteTimeMs());

        log.info("KYB 工商核验流程完成: {} => result={}, cost={}ms", query, raw, result.getExecuteTimeMs());
        return output;
    }
}
