package com.bjjw.rule.client.spring;

import lombok.Data;

import java.util.List;

/**
 * 规则引擎客户端 Spring 配置属性。
 * <p>默认从容器中的 {@link org.springframework.data.redis.connection.RedisConnectionFactory}
 * 或 {@link org.springframework.data.redis.core.StringRedisTemplate} 解析连接；
 * 若使用自定义 Redis 装配，可通过 Bean 名称显式指定。</p>
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
    /**
     * Kafka 执行日志 topic（与 rule-engine-server / 消费端 topic 一致）。
     * <p>容器中存在 {@link org.springframework.kafka.core.KafkaTemplate} 时启用 Kafka 上报，否则退回 HTTP 上报。</p>
     */
    private String kafkaLogTopic = "rule-execution-log";

    /**
     * 管控端项目 ID，用于启动时 HTTP 拉取 SCRIPT/JAVA/BEAN 自定义函数并注册到本地引擎。
     * <p>{@code 0} 表示不同步函数：仅依赖已缓存规则本体时可正常执行；若规则引用了项目函数且未通过 Redis FUNC_UPDATE 注册，将运行时报错。</p>
     */
    private long projectId = 0;
    /** 是否在业务执行时收集表达式 trace 信息，默认 true；与编译性能无关，仅影响运行时是否采集 trace 数据 */
    private boolean traceEnabled = true;
    /** L1 未命中时是否从 Redis L2 读取已发布规则快照 */
    private boolean l2RedisCacheEnabled = true;

    /**
     * 自定义 Redis 连接工厂在 Spring 容器中的 Bean 名称。
     * 设置后优先于按类型注入的 RedisConnectionFactory。
     */
    private String redisConnectionFactoryBeanName;

    /**
     * 自定义 StringRedisTemplate 的 Bean 名称；在未配置 {@link #redisConnectionFactoryBeanName} 且按类型找不到工厂时使用，
     * 将从该模板的 {@link org.springframework.data.redis.core.StringRedisTemplate#getConnectionFactory()} 取连接工厂。
     */
    private String stringRedisTemplateBeanName;

    /**
     * 启动时是否预热全量已缓存脚本（触发 QLExpress4 内部解析缓存），默认 true。
     * <p>开启后即可消除引入 SDK 后“第一次执行慢”的问题。
     * 关闭后可忽略启动预热开销，首次执行时仍会有第一次编译延迟。</p>
     */
    private boolean warmUpOnStart = true;

    /**
     * 内置 HTTP 函数（httpCall/httpGet/httpPost）的 host 白名单，逗号或列表形式配置。
     * <p>为空表示不限制（默认放开，适配 KYB 调用内网核验服务的场景）；配置后仅允许列表内的 host 被访问，
     * 命中失败返回错误 Map、不发起连接。host 大小写不敏感。</p>
     */
    private List<String> allowHosts;
}
