package com.bjjw.rule.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;

@SpringBootApplication(scanBasePackages = "com.bjjw.rule")
@MapperScan("com.bjjw.rule.server.mapper")
public class RuleEngineApplication {
    public static void main(String[] args) {
        SpringApplication.run(RuleEngineApplication.class, args);
    }
}
