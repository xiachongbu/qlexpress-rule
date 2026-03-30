package com.bjjw.rule.example.service;

import com.bjjw.rule.client.RuleEngineClient;
import com.bjjw.rule.example.dto.MultiDimRateQuery;
import com.bjjw.rule.model.dto.RuleResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;

/**
 * 复杂交叉表调用示例：多维场景定价矩阵（四维度交叉查组合定价系数）。
 *
 * <p>对应规则编码：{@code RC_MULTI_DIM_RATE}</p>
 */
@Slf4j
@Service
public class MultiDimRateService {

    private static final String RULE_CODE = "RC_MULTI_DIM_RATE";

    @Resource
    private RuleEngineClient ruleClient;

    /**
     * 按业务类型、结算方式、客户类型、资质四维交叉查询组合定价系数。
     *
     * @param query 交叉表入参
     * @return 定价系数，未命中时可能为 null
     */
    public BigDecimal queryRate(MultiDimRateQuery query) {
        RuleResult result = ruleClient.execute(RULE_CODE, query);

        if (!result.isSuccess()) {
            log.error("复杂交叉表执行失败 [{}]: {}", RULE_CODE, result.getErrorMessage());
            throw new RuntimeException("多维定价查询失败: " + result.getErrorMessage());
        }

        Object value = result.getResult();
        if (value == null) {
            log.warn("复杂交叉表未找到匹配项: {}", query);
            return null;
        }

        BigDecimal rate = new BigDecimal(value.toString());
        log.info("多维定价查询结果: {} => rate={}", query, rate);
        return rate;
    }
}
