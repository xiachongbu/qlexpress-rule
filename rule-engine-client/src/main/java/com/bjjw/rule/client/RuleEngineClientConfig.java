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
    /** 是否在业务执行时收集表达式 trace 信息，默认 true；与编译性能无关，仅影响运行时是否采集 trace 数据 */
    private boolean traceEnabled = true;
    /** L1 未命中时是否从 Redis L2（rule:published:*）读取，默认 true */
    private boolean l2RedisCacheEnabled = true;
    private int logBufferSize = 500;
    private int logBatchSize = 50;
    private int logFlushIntervalMs = 5000;
    private String kafkaLogTopic = "rule-execution-log";
    /**
     * 启动时是否预热全量已缓存脚本（触发 QLExpress4 内部解析缓存），默认 true。
     * <p>开启后即可消除引入 SDK 后“第一次执行慢”的问题。</p>
     */
    private boolean warmUpOnStart = true;
}
