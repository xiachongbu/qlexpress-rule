package com.bjjw.rule.server.controller.sync;

import com.bjjw.rule.model.entity.RuleExecutionLog;
import com.bjjw.rule.server.common.R;
import com.bjjw.rule.server.service.RuleExecutionLogService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/rule/log")
public class LogReportController {

    @Resource
    private RuleExecutionLogService logService;

    @PostMapping("/report")
    public R<Void> report(@RequestBody List<RuleExecutionLog> logs) {
        if (logs != null && !logs.isEmpty()) {
            logService.saveBatch(logs);
        }
        return R.ok();
    }
}
