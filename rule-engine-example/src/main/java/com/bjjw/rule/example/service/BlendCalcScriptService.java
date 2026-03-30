package com.bjjw.rule.example.service;

import com.bjjw.rule.client.RuleEngineClient;
import com.bjjw.rule.example.dto.BlendCalcScriptQuery;
import com.bjjw.rule.model.dto.RuleResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * QL 脚本调用示例：混业场景下多费率产品线按比例分拆计费。
 *
 * <p>对应规则编码：{@code RC_BLEND_CALC_SCRIPT}</p>
 */
@Slf4j
@Service
public class BlendCalcScriptService {

    private static final String RULE_CODE = "RC_BLEND_CALC_SCRIPT";

    @Resource
    private RuleEngineClient ruleClient;

    /**
     * 执行混业组合计费脚本，返回规则输出（通常为 Map）。
     *
     * @param query 脚本入参
     * @return 含 result、success、executeTimeMs 的包装 Map
     */
    public Map<String, Object> executeBlendCalc(BlendCalcScriptQuery query) {
        RuleResult result = ruleClient.execute(RULE_CODE, query);

        if (!result.isSuccess()) {
            log.error("QL脚本执行失败 [{}]: {}", RULE_CODE, result.getErrorMessage());
            throw new RuntimeException("混业计费脚本执行失败: " + result.getErrorMessage());
        }

        Map<String, Object> output = new HashMap<>();
        output.put("result", result.getResult());
        output.put("success", true);
        output.put("executeTimeMs", result.getExecuteTimeMs());

        log.info("混业计费脚本完成: {} => result={}", query, result.getResult());
        return output;
    }
}
