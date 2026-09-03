package com.bjjw.rule.client.spring;

import com.bjjw.rule.client.RuleEngineClient;
import com.bjjw.rule.client.log.ExecutionLogReporter;
import com.bjjw.rule.client.log.KafkaLogReporter;
import com.bjjw.rule.core.function.HttpBuiltinFunctions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.util.StringUtils;

/**
 * 规则引擎客户端自动装配。
 * <p>配置 {@code rule-engine.client.server-url} 后触发；需容器中存在 {@link RedisConnectionFactory}
 * （或 {@link StringRedisTemplate}）用于 L1/L2 缓存。若存在 {@link KafkaTemplate}，则执行日志走 Kafka 上报，
 * 否则回退为 HTTP 上报。</p>
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "rule-engine.client", name = "server-url")
@AutoConfigureAfter(
        value = DataRedisAutoConfiguration.class,
        name = "org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration"
)
public class RuleEngineAutoConfiguration {

    @Bean
    @ConfigurationProperties(prefix = "rule-engine.client")
    public RuleEngineClientProperties ruleEngineClientProperties() {
        return new RuleEngineClientProperties();
    }

    /**
     * 存在 {@link KafkaTemplate} 时注册基于 Kafka 的执行日志上报器。
     */
    @Configuration
    @ConditionalOnClass(KafkaTemplate.class)
    @ConditionalOnBean(KafkaTemplate.class)
    static class KafkaLogReporterConfiguration {

        @Bean
        @ConditionalOnMissingBean(ExecutionLogReporter.class)
        public ExecutionLogReporter kafkaLogReporter(KafkaTemplate<String, String> kafkaTemplate,
                                                     RuleEngineClientProperties props) {
            return new KafkaLogReporter(kafkaTemplate, props.getKafkaLogTopic());
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public RuleEngineClient ruleEngineClient(RuleEngineClientProperties props,
                                             ObjectProvider<RedisConnectionFactory> connectionFactoryProvider,
                                             ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider,
                                             ApplicationContext applicationContext,
                                             ObjectProvider<ExecutionLogReporter> logReporterProvider) {
        // 将配置文件中的 host 白名单注入 core 侧内置 HTTP 函数（为空则不限制）
        HttpBuiltinFunctions.configureAllowHosts(props.getAllowHosts());
        RedisConnectionFactory connectionFactory = resolveRedisConnectionFactory(
                props, applicationContext, connectionFactoryProvider, stringRedisTemplateProvider);
        if (connectionFactory == null) {
            throw new IllegalStateException(
                    "规则引擎客户端需要 Redis：请提供 RedisConnectionFactory 或 StringRedisTemplate，"
                            + "或通过 rule-engine.client.redis-connection-factory-bean-name / string-redis-template-bean-name 指定 Bean 名称。");
        }

        RuleEngineClient.Builder builder = RuleEngineClient.builder()
                .serverUrl(props.getServerUrl())
                .appName(props.getAppName())
                .token(props.getToken())
                .connectionFactory(connectionFactory)
                .applicationContext(applicationContext)
                .l1CacheMaxSize(props.getL1CacheMaxSize())
                .httpTimeoutMs(props.getHttpTimeoutMs())
                .projectId(props.getProjectId())
                .traceEnabled(props.isTraceEnabled())
                .l2RedisCacheEnabled(props.isL2RedisCacheEnabled())
                .warmUpOnStart(props.isWarmUpOnStart());

        ExecutionLogReporter reporter = logReporterProvider.getIfAvailable();
        if (reporter != null) {
            builder.logReporter(reporter);
        }

        RuleEngineClient client = builder.build();
        client.start();
        return client;
    }

    /**
     * 解析 Redis 连接工厂：显式 Bean 名 → 按类型 → StringRedisTemplate 的连接工厂。
     */
    private static RedisConnectionFactory resolveRedisConnectionFactory(
            RuleEngineClientProperties props,
            ApplicationContext applicationContext,
            ObjectProvider<RedisConnectionFactory> connectionFactoryProvider,
            ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider) {
        if (StringUtils.hasText(props.getRedisConnectionFactoryBeanName())) {
            return applicationContext.getBean(props.getRedisConnectionFactoryBeanName(), RedisConnectionFactory.class);
        }
        if (StringUtils.hasText(props.getStringRedisTemplateBeanName())) {
            StringRedisTemplate tpl = applicationContext.getBean(
                    props.getStringRedisTemplateBeanName(), StringRedisTemplate.class);
            return tpl.getConnectionFactory();
        }
        RedisConnectionFactory factory = connectionFactoryProvider.getIfAvailable();
        if (factory != null) {
            return factory;
        }
        StringRedisTemplate stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
        if (stringRedisTemplate != null) {
            return stringRedisTemplate.getConnectionFactory();
        }
        return null;
    }
}
