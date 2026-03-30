package com.bjjw.rule.example.service;

import com.bjjw.rule.client.RuleEngineClient;
import com.bjjw.rule.example.dto.TaxCalcQuery;
import com.bjjw.rule.model.dto.RuleResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * 决策流（Decision Flow）调用示例
 *
 * 业务场景：敞口与费用试算流程（与 data-example.sql 中 RC_EXPOSURE_FLOW 一致）
 * 串行试算：定价费率 → 本金/敞口拆分 → 费用 → 减免策略 → 应收费用。
 *
 * 对应规则编码：RC_EXPOSURE_FLOW
 * 决策流节点示例：
 *   1. 判断客群类型 → 确定适用费率
 *   2. 计算不含税金额 = 含税金额 / (1 + 税率)
 *   3. 计算税额 = 不含税金额 × 税率
 *   4. 判断是否享受减免 → 计算减免金额
 *   5. 计算应纳税额 = 税额 - 减免金额
 */
@Slf4j
@Service
public class TaxCalcFlowService {

    private static final String RULE_CODE = "RC_EXPOSURE_FLOW";

    @Resource
    private RuleEngineClient ruleClient;

    /**
     * 执行敞口与费用试算流程
     *
     * @param query 试算流程参数 DTO
     * @return 计算结果（包含多个输出变量）
     */
    public Map<String, Object> calculateTax(TaxCalcQuery query) {
        RuleResult result = ruleClient.execute(RULE_CODE, query);

        if (!result.isSuccess()) {
            log.error("决策流执行失败 [{}]: {}", RULE_CODE, result.getErrorMessage());
            throw new RuntimeException("敞口与费用试算流程执行失败: " + result.getErrorMessage());
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

        log.info("计税流程完成: {} => result={}, cost={}ms",
                query, raw, result.getExecuteTimeMs());
        return output;
    }
}
