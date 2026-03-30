package com.bjjw.rule.client.spring;

import lombok.Data;

/**
 * 规则引擎客户端Spring配置属性
 * Redis配置完全由客户端服务的spring.redis.*提供，无需额外配置
 */
@Data
public class RuleEngineClientProperties {
    private String serverUrl;
    private String appName = "default";

    /**
     * 访问Token，用于服务端身份认证
     */
    private String token;

    private int l1CacheMaxSize = 1000;
    private int httpTimeoutMs = 3000;
    private String kafkaLogTopic = "rule-execution-log";
    /** 项目 ID，启动时自动从服务端同步函数定义（0 表示不同步函数） */
    private long projectId = 0;
    /** 是否开启表达式追踪，默认 true；关闭可提升执行性能 */
    private boolean traceEnabled = true;
}
