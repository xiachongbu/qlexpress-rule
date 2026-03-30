package com.bjjw.rule.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 规则引擎客户端集成示例
 *
 * 演示如何在业务系统中集成 rule-engine-client SDK，
 * 调用服务端已发布的五种决策模型（决策表/决策树/决策流/交叉表/评分卡）。
 *
 * 启动前提：
 * 1. rule-engine-server 已启动并监听 8080 端口
 * 2. Redis 已启动
 * 3. 在 Server 端创建项目、设计规则并发布
 */
@SpringBootApplication(scanBasePackages = {"com.bjjw.rule.example"})
public class RuleExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(RuleExampleApplication.class, args);
    }
}
