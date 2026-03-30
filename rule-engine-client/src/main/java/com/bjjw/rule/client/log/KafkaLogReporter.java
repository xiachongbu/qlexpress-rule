package com.bjjw.rule.client.log;

import com.bjjw.rule.model.entity.RuleExecutionLog;
import com.alibaba.fastjson.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;

public class KafkaLogReporter implements ExecutionLogReporter {

    private static final Logger log = LoggerFactory.getLogger(KafkaLogReporter.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;

    public KafkaLogReporter(KafkaTemplate<String, String> kafkaTemplate, String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @Override
    public void report(List<RuleExecutionLog> logs) {
        for (RuleExecutionLog logEntry : logs) {
            try {
                String key = logEntry.getRuleCode();
                String value = JSON.toJSONString(logEntry);
                kafkaTemplate.send(topic, key, value);
            } catch (Exception e) {
                log.warn("Kafka log report failed for rule {}: {}", logEntry.getRuleCode(), e.getMessage());
            }
        }
    }
}
