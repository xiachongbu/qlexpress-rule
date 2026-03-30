package com.bjjw.rule.client.log;

import com.bjjw.rule.model.entity.RuleExecutionLog;
import com.alibaba.fastjson.JSON;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class HttpLogReporter implements ExecutionLogReporter {

    private static final Logger log = LoggerFactory.getLogger(HttpLogReporter.class);
    private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");
    private final OkHttpClient httpClient;
    private final String reportUrl;

    public HttpLogReporter(String serverUrl, int timeoutMs) {
        String baseUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
        this.reportUrl = baseUrl + "/api/rule/log/report";
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .build();
    }

    @Override
    public void report(List<RuleExecutionLog> logs) {
        try {
            RequestBody body = RequestBody.create(JSON.toJSONString(logs), JSON_TYPE);
            Request request = new Request.Builder().url(reportUrl).post(body).build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.warn("Log report failed with status: {}", response.code());
                }
            }
        } catch (Exception e) {
            log.warn("Log report error: {}", e.getMessage());
        }
    }
}
