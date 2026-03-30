package com.bjjw.rule.client;

import lombok.Data;

/**
 * 规则引擎客户端配置
 * Redis配置完全由客户端服务的Spring Data Redis提供
 */
@Data
public class RuleEngineClientConfig {
    private String serverUrl = "http://localhost:8080";
    private String appName = "default";
    
    /**
     * 访问Token，用于服务端身份认证
     */
    private String token;
    
    private int l1CacheMaxSize = 1000;
    private int httpTimeoutMs = 3000;
    private int heartbeatIntervalMs = 300000;
    private boolean logReportEnabled = true;
    /** 项目 ID，用于从服务端同步函数定义（0 表示不同步函数） */
    private long projectId = 0;
    /** 是否开启表达式追踪，默认 true；关闭可提升执行性能 */
    private boolean traceEnabled = true;
    private int logBufferSize = 500;
    private int logBatchSize = 50;
    private int logFlushIntervalMs = 5000;
    private String kafkaLogTopic = "rule-execution-log";
}
