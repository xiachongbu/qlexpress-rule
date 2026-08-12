package com.bjjw.rule.example.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * KYB 风控 HTTP 调用示例的 <b>Mock 外部接口</b>。
 *
 * <p>为使 HTTP 调用示例（决策流 RC_KYB_HTTP_FLOW、QL 脚本 RC_KYB_HTTP_SCRIPT）自包含、
 * 可直接联调，这里用本地接口模拟真实的「工商核验」与「黑名单查询」服务。
 * 规则里的 httpCall/httpPost 目标 URL 即指向下述端点。</p>
 *
 * <p>返回结构与规则脚本的访问路径对应（响应 JSON 顶层字段被 httpCall 平铺，可用短路径）：
 * <ul>
 *   <li>工商核验：脚本读取 {@code kybResp.data.riskLevel}</li>
 *   <li>黑名单查询：脚本读取 {@code resp.data.hit} 与 {@code resp.data.type}</li>
 * </ul>
 * 生产环境应将规则中的 URL 替换为真实服务地址，本 Mock 可删除。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/example/mock")
public class MockExternalApiController {

    /**
     * 模拟工商信息核验接口。
     *
     * <p>简单规则：商户名称含「风险」判为 HIGH；含「关注」判为 MEDIUM；其余为 LOW。</p>
     *
     * 请求示例：
     * POST /api/example/mock/kyb-verify
     * { "merchantId": "M202600001", "merchantName": "示例科技有限公司" }
     */
    @PostMapping("/kyb-verify")
    public Map<String, Object> kybVerify(@RequestBody(required = false) Map<String, Object> body,
                                         @RequestHeader(value = "Authorization", required = false) String authorization) {
        String merchantId = body != null && body.get("merchantId") != null ? String.valueOf(body.get("merchantId")) : "";
        String merchantName = body != null && body.get("merchantName") != null ? String.valueOf(body.get("merchantName")) : "";
        log.info("[MockKYB] 工商核验请求 merchantId={}, merchantName={}, auth={}", merchantId, merchantName, authorization);

        String riskLevel;
        if (merchantName.contains("风险")) {
            riskLevel = "HIGH";
        } else if (merchantName.contains("关注")) {
            riskLevel = "MEDIUM";
        } else {
            riskLevel = "LOW";
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("merchantId", merchantId);
        data.put("merchantName", merchantName);
        data.put("riskLevel", riskLevel);
        data.put("registered", true);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("code", 0);
        resp.put("message", "ok");
        resp.put("data", data);
        return resp;
    }

    /**
     * 模拟法人黑名单查询接口。
     *
     * <p>简单规则：法人证件号包含「9999」判为命中（涉诉类型），否则未命中。</p>
     *
     * 请求示例：
     * POST /api/example/mock/blacklist-query
     * { "merchantId": "M202600001", "idCard": "110101199001010011" }
     */
    @PostMapping("/blacklist-query")
    public Map<String, Object> blacklistQuery(@RequestBody(required = false) Map<String, Object> body,
                                              @RequestHeader(value = "Authorization", required = false) String authorization) {
        String idCard = body != null && body.get("idCard") != null ? String.valueOf(body.get("idCard")) : "";
        log.info("[MockBlacklist] 黑名单查询请求 idCard={}, auth={}", idCard, authorization);

        boolean hit = idCard.contains("9999");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("hit", hit);
        data.put("type", hit ? "涉诉风险" : null);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("code", 0);
        resp.put("message", "ok");
        resp.put("data", data);
        return resp;
    }
}
