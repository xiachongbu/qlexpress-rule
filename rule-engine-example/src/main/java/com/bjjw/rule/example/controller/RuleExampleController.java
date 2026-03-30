package com.bjjw.rule.example.controller;

import com.bjjw.rule.client.RuleEngineClient;
import com.bjjw.rule.client.cache.CachedRule;
import com.bjjw.rule.example.dto.*;
import com.bjjw.rule.example.service.*;
import com.bjjw.rule.model.dto.RuleResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 规则引擎客户端集成示例 - REST API（与 rule-engine-server 的 data-example.sql / RISK_DEMO 示例对齐）
 *
 * 提供多种决策模型的调用入口，演示完整的请求/响应流程。
 * 业务接口使用 DTO 接收参数，SDK 将 DTO 转为 Map 注入规则上下文。
 */
@Slf4j
@RestController
@RequestMapping("/api/example")
public class RuleExampleController {

    @Resource
    private TaxRateService taxRateService;
    @Resource
    private TaxpayerClassifyService taxpayerClassifyService;
    @Resource
    private TaxCalcFlowService taxCalcFlowService;
    @Resource
    private RateLookupService rateLookupService;
    @Resource
    private RiskAssessService riskAssessService;
    @Resource
    private MultiDimRateService multiDimRateService;
    @Resource
    private InvoiceFraudScoreService invoiceFraudScoreService;
    @Resource
    private BlendCalcScriptService blendCalcScriptService;
    @Resource
    private TaxRateByCtxService taxRateByCtxService;
    @Resource
    private RuleEngineClient ruleClient;

    // ==================== 决策表（Decision Table） ====================

    /**
     * 决策表示例：RC_PRICING_TABLE 客群×产品线定价表（DTO 传参）
     *
     * 请求示例：
     * POST /api/example/tax-rate
     * {
     *   "taxpayerType": "一般纳税人",
     *   "goodsCategory": "货物"
     * }
     */
    @PostMapping("/tax-rate")
    public Map<String, Object> queryTaxRate(@RequestBody TaxRateQuery query) {
        BigDecimal taxRate = taxRateService.queryTaxRate(query);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("modelType", "TABLE");
        Map<String, Object> data = new HashMap<>();
        data.put("taxRate", taxRate);
        data.put("taxpayerType", query.getTaxpayerType());
        data.put("goodsCategory", query.getGoodsCategory());
        result.put("data", data);
        return result;
    }

    // ==================== 决策树（Decision Tree） ====================

    /**
     * 决策树示例：RC_CREDIT_TREE 客户信用分层（DTO 传参）
     *
     * 请求示例：
     * POST /api/example/taxpayer-classify
     * {
     *   "annualRevenue": 5000,
     *   "taxComplianceScore": 85,
     *   "yearsInBusiness": 10,
     *   "hasViolation": false
     * }
     */
    @PostMapping("/taxpayer-classify")
    public Map<String, Object> classifyTaxpayer(@RequestBody TaxpayerClassifyQuery query) {
        String creditLevel = taxpayerClassifyService.classifyCreditLevel(query);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("modelType", "TREE");
        Map<String, Object> data = new HashMap<>();
        data.put("creditLevel", creditLevel);
        result.put("data", data);
        return result;
    }

    // ==================== 决策流（Decision Flow） ====================

    /**
     * 决策流示例：RC_EXPOSURE_FLOW 敞口与费用试算流程（DTO 传参）
     *
     * 请求示例：
     * POST /api/example/tax-calc
     * {
     *   "taxpayerType": "一般纳税人",
     *   "totalAmount": 113000,
     *   "goodsCategory": "货物",
     *   "isExempt": false
     * }
     */
    @PostMapping("/tax-calc")
    public Map<String, Object> calculateTax(@RequestBody TaxCalcQuery query) {
        Map<String, Object> calcResult = taxCalcFlowService.calculateTax(query);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("modelType", "FLOW");
        result.put("data", calcResult);
        return result;
    }

    // ==================== 交叉表（Cross Table） ====================

    /**
     * 交叉表示例：RC_RATE_MATRIX 二维风险参数矩阵（DTO 传参）
     *
     * 请求示例：
     * POST /api/example/rate-lookup
     * {
     *   "taxpayerType": "一般纳税人",
     *   "goodsCategory": "服务"
     * }
     */
    @PostMapping("/rate-lookup")
    public Map<String, Object> lookupRate(@RequestBody RateLookupQuery query) {
        BigDecimal rate = rateLookupService.lookupRate(query);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("modelType", "CROSS");
        Map<String, Object> data = new HashMap<>();
        data.put("taxRate", rate);
        data.put("taxpayerType", query.getTaxpayerType());
        data.put("goodsCategory", query.getGoodsCategory());
        result.put("data", data);
        return result;
    }

    // ==================== 评分卡（Scorecard） ====================

    /**
     * 评分卡示例：RC_RISK_SCORECARD 综合风险评分卡（DTO 传参）
     *
     * 请求示例：
     * POST /api/example/risk-assess
     * {
     *   "creditLevel": "B",
     *   "annualRevenue": 3000,
     *   "yearsInBusiness": 5,
     *   "taxBurdenDeviation": 0.15,
     *   "violationCount": 1
     * }
     */
    @PostMapping("/risk-assess")
    public Map<String, Object> assessRisk(@RequestBody RiskAssessQuery query) {
        Map<String, Object> assessment = riskAssessService.assessRisk(query);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("modelType", "SCORE");
        result.put("data", assessment);
        return result;
    }

    // ==================== 函数调用（JAVA / BEAN / SCRIPT —— 客户端本地执行） ====================

    // ==================== 对象传入（Object Context） ====================

    /**
     * 对象传入示例：RC_PRICING_BY_OBJECT 对象传入定价（DTO 平铺字段，与规则变量一致）
     *
     * 请求示例：
     * POST /api/example/tax-rate-by-context
     * {
     *   "taxpayerType": "一般纳税人",
     *   "goodsCategory": "货物"
     * }
     */
    @PostMapping("/tax-rate-by-context")
    public Map<String, Object> queryTaxRateByContext(@RequestBody TaxRateByCtxQuery query) {
        Object taxRate = taxRateByCtxService.queryTaxRate(query);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("modelType", "TABLE");
        Map<String, Object> data = new HashMap<>();
        data.put("taxRate", taxRate);
        data.put("ctx", query);
        result.put("data", data);
        return result;
    }

    // ==================== 复杂交叉表（Advanced Cross Table） ====================

    /**
     * 复杂交叉表示例：多维场景定价矩阵
     *
     * 请求示例：
     * POST /api/example/risk/multi-dim-rate
     * {
     *   "serviceType": "基础通信",
     *   "paymentMode": "后付费",
     *   "customerType": "企业客户",
     *   "taxpayerQualification": "一般纳税人"
     * }
     */
    @PostMapping("/risk/multi-dim-rate")
    public Map<String, Object> queryMultiDimRate(@RequestBody MultiDimRateQuery query) {
        BigDecimal rate = multiDimRateService.queryRate(query);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("modelType", "CROSS_ADV");
        Map<String, Object> data = new HashMap<>();
        data.put("multiDimRate", rate);
        data.put("serviceType", query.getServiceType());
        data.put("paymentMode", query.getPaymentMode());
        data.put("customerType", query.getCustomerType());
        data.put("taxpayerQualification", query.getTaxpayerQualification());
        result.put("data", data);
        return result;
    }

    // ==================== 复杂评分卡（Advanced Scorecard） ====================

    /**
     * 复杂评分卡示例：交易票据异常评分（分组维度）
     *
     * 请求示例：
     * POST /api/example/risk/invoice-fraud-score
     * {
     *   "customerLevel": "金",
     *   "monthlyConsumption": 5000,
     *   "invoiceDeviationRate": 0.05,
     *   "redInvoiceRatio": 0.02,
     *   "zeroRateInvoiceRatio": 0.01,
     *   "crossRegionInvoiceRatio": 0.08
     * }
     */
    @PostMapping("/risk/invoice-fraud-score")
    public Map<String, Object> assessInvoiceFraudScore(@RequestBody InvoiceFraudScoreQuery query) {
        Map<String, Object> assessment = invoiceFraudScoreService.assessRisk(query);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("modelType", "SCORE_ADV");
        result.put("data", assessment);
        return result;
    }

    // ==================== QL脚本（Script） ====================

    /**
     * QL脚本示例：混业组合计费（直接脚本执行）
     *
     * 请求示例：
     * POST /api/example/risk/blend-calc
     * {
     *   "taxpayerQualification": "一般纳税人",
     *   "billingAmount": 100000,
     *   "basicServiceRatio": 0.6,
     *   "vasServiceRatio": 0.4
     * }
     */
    @PostMapping("/risk/blend-calc")
    public Map<String, Object> calcBlendScript(@RequestBody BlendCalcScriptQuery query) {
        Map<String, Object> calcResult = blendCalcScriptService.executeBlendCalc(query);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("modelType", "SCRIPT");
        result.put("data", calcResult);
        return result;
    }

    // ==================== 原有函数调用 ====================

    /**
     * JAVA 函数调用示例：本地客户端执行，函数通过 FunctionConfig 注册到 SDK 引擎
     *
     * 请求示例：
     * POST /api/example/calc-vat-java
     * { "totalAmount": 113000 }
     */
    @PostMapping("/calc-vat-java")
    public Map<String, Object> calcVatJava(@RequestBody Map<String, Object> request) {
        Map<String, Object> params = new HashMap<>();
        params.put("totalAmount", request.getOrDefault("totalAmount", 113000));

        RuleResult ruleResult = ruleClient.execute("RC_FLOW_JAVA_SAMPLE", params);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", ruleResult.isSuccess());
        result.put("funcType", "JAVA");
        result.put("funcName", "calculateVAT");
        result.put("description", "通过 new TaxFunctions() 反射实例化，注册到客户端 SDK 引擎");
        result.put("result", ruleResult.getResult());
        result.put("executeTimeMs", ruleResult.getExecuteTimeMs());
        if (ruleResult.getTraces() != null) {
            result.put("traces", ruleResult.getTraces());
        }
        if (ruleResult.getErrorMessage() != null) {
            result.put("errorMessage", ruleResult.getErrorMessage());
        }
        return result;
    }

    /**
     * BEAN 函数调用示例：本地客户端执行，函数通过 FunctionConfig 注册到 SDK 引擎
     *
     * 请求示例：
     * POST /api/example/calc-vat-bean
     * { "totalAmount": 113000 }
     */
    @PostMapping("/calc-vat-bean")
    public Map<String, Object> calcVatBean(@RequestBody Map<String, Object> request) {
        Map<String, Object> params = new HashMap<>();
        params.put("totalAmount", request.getOrDefault("totalAmount", 113000));

        RuleResult ruleResult = ruleClient.execute("RC_FLOW_BEAN_SAMPLE", params);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", ruleResult.isSuccess());
        result.put("funcType", "BEAN");
        result.put("funcName", "calcTaxByBean");
        result.put("description", "通过 Spring Bean taxFunctions 注入，注册到客户端 SDK 引擎");
        result.put("result", ruleResult.getResult());
        result.put("executeTimeMs", ruleResult.getExecuteTimeMs());
        if (ruleResult.getTraces() != null) {
            result.put("traces", ruleResult.getTraces());
        }
        if (ruleResult.getErrorMessage() != null) {
            result.put("errorMessage", ruleResult.getErrorMessage());
        }
        return result;
    }

    /**
     * SCRIPT 函数调用示例：本地客户端执行，函数通过 FunctionConfig 注册到 SDK 引擎
     *
     * 请求示例：
     * POST /api/example/calc-vat-script
     * { "taxpayerType": "一般纳税人", "totalAmount": 113000, "goodsCategory": "货物", "isExempt": false }
     */
    @PostMapping("/calc-vat-script")
    public Map<String, Object> calcVatScript(@RequestBody TaxCalcQuery query) {
        RuleResult ruleResult = ruleClient.execute("RC_EXPOSURE_FLOW", query);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", ruleResult.isSuccess());
        result.put("funcType", "SCRIPT");
        result.put("funcName", "roundTax");
        result.put("description", "通过 addFunctionsDefinedInScript 注册 QLExpress 脚本函数到客户端 SDK");
        result.put("result", ruleResult.getResult());
        result.put("executeTimeMs", ruleResult.getExecuteTimeMs());
        if (ruleResult.getTraces() != null) {
            result.put("traces", ruleResult.getTraces());
        }
        if (ruleResult.getErrorMessage() != null) {
            result.put("errorMessage", ruleResult.getErrorMessage());
        }
        return result;
    }

    /**
     * 查询客户端已注册的函数信息（本地配置的函数列表）
     *
     * 请求示例：GET /api/example/functions
     */
    @GetMapping("/functions")
    public Map<String, Object> listFunctions() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", new Object[]{
            funcInfo("roundTax", "金额四舍五入", "SCRIPT", "amount(NUMBER)", "NUMBER", "QLExpress 脚本函数，保留2位小数"),
            funcInfo("formatAmount", "金额格式化", "SCRIPT", "amount(NUMBER)", "STRING", "QLExpress 脚本函数，输出带千分位格式"),
            funcInfo("calculateVAT", "费用试算(Java)", "JAVA", "amount(NUMBER), rate(NUMBER)", "NUMBER", "反射调用 TaxFunctions.calculateVAT"),
            funcInfo("calcTaxByBean", "费用试算(Bean)", "BEAN", "amount(NUMBER), rate(NUMBER)", "NUMBER", "Spring Bean taxFunctions.calculateVAT")
        });
        return result;
    }

    private Map<String, String> funcInfo(String code, String name, String type, String params, String returnType, String desc) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("funcCode", code);
        m.put("funcName", name);
        m.put("implType", type);
        m.put("params", params);
        m.put("returnType", returnType);
        m.put("description", desc);
        return m;
    }

    // ==================== 通用工具接口 ====================

    /**
     * 查询缓存中的规则信息
     *
     * 请求示例：GET /api/example/rule-info/RC_PRICING_TABLE
     */
    @GetMapping("/rule-info/{ruleCode}")
    public Map<String, Object> getRuleInfo(@PathVariable String ruleCode) {
        CachedRule cached = ruleClient.getRuleInfo(ruleCode);
        Map<String, Object> result = new HashMap<>();
        if (cached == null) {
            result.put("success", false);
            result.put("message", "规则未在本地缓存中找到: " + ruleCode);
        } else {
            result.put("success", true);
            Map<String, Object> info = new HashMap<>();
            info.put("ruleCode", cached.getRuleCode());
            info.put("version", cached.getVersion());
            info.put("modelType", cached.getModelType());
            info.put("compiledType", cached.getCompiledType());
            info.put("lastUpdateTime", cached.getLastUpdateTime());
            result.put("data", info);
        }
        return result;
    }

    /**
     * 手动刷新指定规则的本地缓存
     *
     * 请求示例：POST /api/example/refresh/RC_PRICING_TABLE
     */
    @PostMapping("/refresh/{ruleCode}")
    public Map<String, Object> refreshRule(@PathVariable String ruleCode) {
        ruleClient.refreshRule(ruleCode);
        CachedRule cached = ruleClient.getRuleInfo(ruleCode);
        Map<String, Object> result = new HashMap<>();
        result.put("success", cached != null);
        result.put("message", cached != null ? "刷新成功，当前版本: " + cached.getVersion() : "规则未找到: " + ruleCode);
        return result;
    }

    /**
     * 手动刷新全部规则缓存
     *
     * 请求示例：POST /api/example/refresh-all
     */
    @PostMapping("/refresh-all")
    public Map<String, Object> refreshAll() {
        ruleClient.refreshAll();
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "全量刷新已完成");
        return result;
    }

    /**
     * 通用规则执行接口 - 直接传入 ruleCode 和参数
     * 适用于快速测试任意已发布规则
     *
     * 请求示例：
     * POST /api/example/execute
     * {
     *   "ruleCode": "RC_PRICING_TABLE",
     *   "params": { "taxpayerType": "一般纳税人", "goodsCategory": "货物" }
     * }
     */
    @PostMapping("/execute")
    @SuppressWarnings("unchecked")
    public RuleResult executeRule(@RequestBody Map<String, Object> request) {
        String ruleCode = request.get("ruleCode").toString();
        Map<String, Object> params = (Map<String, Object>) request.getOrDefault("params", new HashMap<>());
        return ruleClient.execute(ruleCode, params);
    }
}
