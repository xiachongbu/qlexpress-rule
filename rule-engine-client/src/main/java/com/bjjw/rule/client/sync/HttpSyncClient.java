package com.bjjw.rule.client.sync;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.bjjw.rule.client.cache.CachedRule;
import com.bjjw.rule.client.cache.CachedRuleSet;
import com.bjjw.rule.model.constant.RuleCompIds;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * HTTP 拉取已发布规则；每条请求显式传入 compId，与服务端 {@code /api/rule/sync} 一致。
 */
public class HttpSyncClient {

    private static final Logger log = LoggerFactory.getLogger(HttpSyncClient.class);
    private final OkHttpClient httpClient;
    private final String serverUrl;
    private final String token;
    private final String projectCode;

    public HttpSyncClient(String serverUrl, int timeoutMs, String token, String projectCode) {
        this.serverUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
        this.token = token;
        this.projectCode = projectCode;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .build();
    }

    /**
     * 拉取单条规则（服务端按 compId 省优先、无则回落全国）
     *
     * @param ruleCode 规则编码
     * @param compId   作用域 compId，null/空 视为全国
     */
    public CachedRule fetchRule(String ruleCode, String compId) {
        String cid = RuleCompIds.normalize(compId);
        try {
            HttpUrl url = HttpUrl.parse(serverUrl + "/api/rule/sync/" + ruleCode).newBuilder()
                    .addQueryParameter("compId", cid)
                    .addQueryParameter("projectCode", projectCode)
                    .build();
            Request.Builder requestBuilder = new Request.Builder()
                    .url(url)
                    .get();
            if (token != null && !token.isEmpty()) {
                requestBuilder.header("X-Rule-Token", token);
            }
            Request request = requestBuilder.build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    JSONObject json = JSON.parseObject(response.body().string());
                    if (json.getIntValue("code") == 200 && json.get("data") != null) {
                        return toCachedRule(json.getJSONObject("data"));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch rule {}: {}", ruleCode, e.getMessage());
        }
        return null;
    }

    /**
     * 全量拉取指定 compId 下解析后的规则列表（常用于启动时预热全国默认规则）
     */
    public List<CachedRule> fetchAll(String compId) {
        String cid = RuleCompIds.normalize(compId);
        List<CachedRule> rules = new ArrayList<>();
        try {
            HttpUrl url = HttpUrl.parse(serverUrl + "/api/rule/sync/all").newBuilder()
                    .addQueryParameter("compId", cid)
                    .addQueryParameter("projectCode", projectCode)
                    .build();
            Request.Builder requestBuilder = new Request.Builder()
                    .url(url)
                    .get();
            if (token != null && !token.isEmpty()) {
                requestBuilder.header("X-Rule-Token", token);
            }
            Request request = requestBuilder.build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    JSONObject json = JSON.parseObject(response.body().string());
                    if (json.getIntValue("code") == 200) {
                        JSONArray arr = json.getJSONArray("data");
                        if (arr != null) {
                            for (int i = 0; i < arr.size(); i++) {
                                CachedRule r = toCachedRule(arr.getJSONObject(i));
                                if (r != null) {
                                    rules.add(r);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch all rules: {}", e.getMessage());
        }
        return rules;
    }

    /**
     * 拉取单条已发布规则集（省优先回落全国与单规则一致）
     */
    public CachedRuleSet fetchRuleSet(String setCode, String compId) {
        if (setCode == null || setCode.isEmpty()) {
            return null;
        }
        String cid = RuleCompIds.normalize(compId);
        try {
            HttpUrl url = HttpUrl.parse(serverUrl + "/api/rule/sync/set/" + setCode).newBuilder()
                    .addQueryParameter("compId", cid)
                    .addQueryParameter("projectCode", projectCode)
                    .build();
            Request.Builder requestBuilder = new Request.Builder().url(url).get();
            if (token != null && !token.isEmpty()) {
                requestBuilder.header("X-Rule-Token", token);
            }
            try (Response response = httpClient.newCall(requestBuilder.build()).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    JSONObject json = JSON.parseObject(response.body().string());
                    if (json.getIntValue("code") == 200 && json.get("data") != null) {
                        return toCachedRuleSet(json.getJSONObject("data"));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch rule set {}: {}", setCode, e.getMessage());
        }
        return null;
    }

    /**
     * 全量拉取指定 compId 下解析后的已上线规则集列表
     */
    public List<CachedRuleSet> fetchAllSets(String compId) {
        String cid = RuleCompIds.normalize(compId);
        List<CachedRuleSet> list = new ArrayList<>();
        try {
            HttpUrl url = HttpUrl.parse(serverUrl + "/api/rule/sync/set/all").newBuilder()
                    .addQueryParameter("compId", cid)
                    .addQueryParameter("projectCode", projectCode)
                    .build();
            Request.Builder requestBuilder = new Request.Builder().url(url).get();
            if (token != null && !token.isEmpty()) {
                requestBuilder.header("X-Rule-Token", token);
            }
            try (Response response = httpClient.newCall(requestBuilder.build()).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    JSONObject json = JSON.parseObject(response.body().string());
                    if (json.getIntValue("code") == 200) {
                        JSONArray arr = json.getJSONArray("data");
                        if (arr != null) {
                            for (int i = 0; i < arr.size(); i++) {
                                CachedRuleSet s = toCachedRuleSet(arr.getJSONObject(i));
                                if (s != null) {
                                    list.add(s);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch all rule sets: {}", e.getMessage());
        }
        return list;
    }

    /**
     * 从服务端拉取项目函数列表（JAVA/BEAN/SCRIPT 类型）
     */
    public List<JSONObject> fetchFunctions(long projectId) {
        List<JSONObject> functions = new ArrayList<>();
        try {
            Request.Builder requestBuilder = new Request.Builder()
                    .url(serverUrl + "/api/rule/sync/functions/" + projectId)
                    .get();
            if (token != null && !token.isEmpty()) {
                requestBuilder.header("X-Rule-Token", token);
            }
            Request request = requestBuilder.build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    JSONObject json = JSON.parseObject(response.body().string());
                    if (json.getIntValue("code") == 200) {
                        JSONArray arr = json.getJSONArray("data");
                        if (arr != null) {
                            for (int i = 0; i < arr.size(); i++) {
                                functions.add(arr.getJSONObject(i));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch functions for project {}: {}", projectId, e.getMessage());
        }
        return functions;
    }

    private CachedRule toCachedRule(JSONObject obj) {
        if (obj == null) {
            return null;
        }
        CachedRule rule = new CachedRule();
        rule.setRuleCode(obj.getString("ruleCode"));
        rule.setProjectCode(obj.getString("projectCode"));
        if (obj.containsKey("compId") && obj.getString("compId") != null) {
            rule.setCompId(RuleCompIds.normalize(obj.getString("compId")));
        } else {
            rule.setCompId(RuleCompIds.NATIONAL);
        }
        rule.setVersion(obj.getIntValue("version"));
        rule.setModelType(obj.getString("modelType"));
        rule.setCompiledScript(obj.getString("compiledScript"));
        rule.setCompiledType(obj.getString("compiledType"));
        rule.setModelJson(obj.getString("modelJson"));
        // HTTP 返回的是 RulePublished JSON，字段名 preciseMode；旧服务端无此字段时为 0 → false
        rule.setPrecise(obj.getIntValue("preciseMode") == 1);
        // HTTP 返回的是 RulePublished JSON，字段名 timeoutMillis；旧服务端无此字段时为 0 → 不限制
        rule.setTimeoutMillis(obj.getLongValue("timeoutMillis"));
        // HTTP 返回的是 RulePublished JSON，字段名 reportLog；缺字段时默认 1（开）
        rule.setReportLog(obj.containsKey("reportLog") ? obj.getIntValue("reportLog") : 1);
        rule.setLastUpdateTime(System.currentTimeMillis());
        return rule;
    }

    private CachedRuleSet toCachedRuleSet(JSONObject obj) {
        if (obj == null) {
            return null;
        }
        String setCode = obj.getString("setCode");
        if (setCode == null || setCode.isEmpty()) {
            return null;
        }
        CachedRuleSet s = new CachedRuleSet();
        s.setSetCode(setCode);
        s.setProjectCode(obj.getString("projectCode"));
        if (obj.containsKey("compId") && obj.getString("compId") != null) {
            s.setCompId(RuleCompIds.normalize(obj.getString("compId")));
        } else {
            s.setCompId(RuleCompIds.NATIONAL);
        }
        s.setVersion(obj.getIntValue("version"));
        s.setMemberRuleCodes(parseMemberRuleCodesField(obj));
        s.setHitPolicy(obj.getString("hitPolicy"));
        // 缺字段时默认 1（开）
        s.setReportLog(obj.containsKey("reportLog") ? obj.getIntValue("reportLog") : 1);
        s.setLastUpdateTime(System.currentTimeMillis());
        return s;
    }

    private static List<String> parseMemberRuleCodesField(JSONObject obj) {
        List<String> out = new ArrayList<>();
        Object raw = obj.get("memberRuleCodes");
        if (raw == null) {
            return out;
        }
        if (raw instanceof JSONArray) {
            JSONArray arr = (JSONArray) raw;
            for (int i = 0; i < arr.size(); i++) {
                out.add(arr.getString(i));
            }
            return out;
        }
        if (raw instanceof String) {
            JSONArray arr = JSON.parseArray((String) raw);
            if (arr != null) {
                for (int i = 0; i < arr.size(); i++) {
                    out.add(arr.getString(i));
                }
            }
        }
        return out;
    }
}
