package com.bjjw.rule.client.spring;

import com.bjjw.rule.client.RuleEngineClient;
import com.bjjw.rule.client.log.ExecutionLogReporter;
import com.bjjw.rule.client.log.KafkaLogReporter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.kafka.core.KafkaTemplate;

@Configuration
@ConditionalOnProperty(prefix = "rule-engine.client", name = "server-url")
@AutoConfigureAfter({RedisAutoConfiguration.class, KafkaAutoConfiguration.class})
public class RuleEngineAutoConfiguration {

    @Bean
    @ConfigurationProperties(prefix = "rule-engine.client")
    public RuleEngineClientProperties ruleEngineClientProperties() {
        return new RuleEngineClientProperties();
    }

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
    @ConditionalOnBean(RedisConnectionFactory.class)
    public RuleEngineClient ruleEngineClient(RuleEngineClientProperties props,
                                              RedisConnectionFactory connectionFactory,
                                              ApplicationContext applicationContext,
                                              ObjectProvider<ExecutionLogReporter> logReporterProvider) {
        RuleEngineClient.Builder builder = RuleEngineClient.builder()
                .serverUrl(props.getServerUrl())
                .appName(props.getAppName())
                .token(props.getToken())
                .connectionFactory(connectionFactory)
                .applicationContext(applicationContext)
                .l1CacheMaxSize(props.getL1CacheMaxSize())
                .httpTimeoutMs(props.getHttpTimeoutMs())
                .projectId(props.getProjectId())
                .traceEnabled(props.isTraceEnabled());

        ExecutionLogReporter reporter = logReporterProvider.getIfAvailable();
        if (reporter != null) {
            builder.logReporter(reporter);
        }

        RuleEngineClient client = builder.build();
        client.start();
        return client;
    }
}
