package com.bjjw.rule.server.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查接口。
 */
@RestController
public class HealthController {

    @GetMapping({"/api/health"})
    public String health() {
        return "ok";
    }
}
