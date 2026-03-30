package com.bjjw.rule.client.config;

import lombok.Data;

@Data
public class ClientConfig {
    private String serverUrl;
    private String appName = "default";
    private String redisHost;
    private int l1CacheMaxSize = 1000;
    private int httpTimeoutMs = 3000;
    private int logBatchSize = 50;
    private int logFlushIntervalMs = 5000;
    private boolean logReportEnabled = true;
}
