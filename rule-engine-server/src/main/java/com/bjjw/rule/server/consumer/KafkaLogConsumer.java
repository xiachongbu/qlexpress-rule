package com.bjjw.rule.server.consumer;

import cn.hutool.core.collection.CollUtil;
import com.bjjw.rule.model.entity.RuleExecutionLog;
import com.bjjw.rule.server.service.RuleExecutionLogService;
import com.alibaba.fastjson.JSON;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * Kafka 批量消费规则执行日志（适配全局 listener.type=batch 配置）。
 * 仅当配置了 {@code spring.kafka.bootstrap-servers} 时启用；默认示例工程不配置 Kafka，避免误连外网集群。
 */
@Component
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class KafkaLogConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaLogConsumer.class);

    @Resource
    private RuleExecutionLogService logService;

    @KafkaListener(topics = "${rule-engine.kafka.log-topic:rule-execution-log}",
                   groupId = "${spring.kafka.consumer.group-id:rule-engine-server}")
    public void onMessage(List<ConsumerRecord<String, String>> records) {
        List<RuleExecutionLog> list = new ArrayList<>();
        for (ConsumerRecord<String, String> record : records) {
            try {
                RuleExecutionLog entry = JSON.parseObject(record.value(), RuleExecutionLog.class);
                if (entry == null) {
                    log.warn("Kafka log message deserialized to null, offset={}", record.offset());
                    continue;
                }
                list.add(entry);
            } catch (Exception e) {
                log.error("Failed to parse kafka log, offset={}, error={}", record.offset(), e.getMessage(), e);
            }
        }
        if (CollUtil.isNotEmpty(list)) {
            logService.saveBatch(list);
            log.debug("Kafka log batch inserted, size={}", list.size());
        }
    }
}
