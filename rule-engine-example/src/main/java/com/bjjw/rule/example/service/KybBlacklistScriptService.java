package com.bjjw.rule.example.service;

import com.bjjw.rule.client.RuleEngineClient;
import com.bjjw.rule.example.dto.KybBlacklistScriptQuery;
import com.bjjw.rule.model.dto.RuleResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * QL 脚本 HTTP 调用示例：商户黑名单查询。
 *
 * <p>对应规则编码：{@code RC_KYB_HTTP_SCRIPT}。脚本直接调用内置 {@code httpPost} 查询法人黑名单，
 * 依据返回命中情况输出 riskDecision（PASS/REJECT/ERROR）。</p>
 */
@Slf4j
@Service
public class KybBlacklistScriptService {

    private static final String RULE_CODE = "RC_KYB_HTTP_SCRIPT";

    @Resource
    private RuleEngineClient ruleClient;

    /**
     * 执行黑名单查询脚本。
     *
     * @param query 查询参数（商户编号/法人证件号/令牌）
     * @return 含 result、success、executeTimeMs 的包装 Map
     */
    public Map<String, Object> queryBlacklist(KybBlacklistScriptQuery query) {
        RuleResult result = ruleClient.execute(RULE_CODE, query, null);

        if (!result.isSuccess()) {
            log.error("KYB 黑名单脚本执行失败 [{}]: {}", RULE_CODE, result.getErrorMessage());
            throw new RuntimeException("商户黑名单查询脚本执行失败: " + result.getErrorMessage());
        }

        Map<String, Object> output = new HashMap<>();
        output.put("result", result.getResult());
        output.put("success", true);
        output.put("executeTimeMs", result.getExecuteTimeMs());

        log.info("KYB 黑名单查询完成: {} => result={}", query, result.getResult());
        return output;
    }
}
