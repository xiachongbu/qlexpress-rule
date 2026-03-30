package com.bjjw.rule.server.controller.mgmt;

import com.bjjw.rule.model.entity.RuleExecutionLog;
import com.bjjw.rule.server.common.R;
import com.bjjw.rule.server.service.RuleExecutionLogService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/rule/log")
public class ExecutionLogController {
    @Resource
    private RuleExecutionLogService logService;

    @GetMapping("/list")
    public R<IPage<RuleExecutionLog>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String ruleCode,
            @RequestParam(required = false) String projectCode,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        LambdaQueryWrapper<RuleExecutionLog> wrapper = new LambdaQueryWrapper<>();
        if (source != null && !source.isEmpty()) wrapper.eq(RuleExecutionLog::getSource, source);
        if (projectCode != null && !projectCode.isEmpty()) wrapper.eq(RuleExecutionLog::getProjectCode, projectCode);
        if (ruleCode != null && !ruleCode.isEmpty()) wrapper.eq(RuleExecutionLog::getRuleCode, ruleCode);
        // create_time 范围条件，命中分区裁剪（PARTITION BY RANGE(TO_DAYS(create_time))）
        if (startTime != null) wrapper.ge(RuleExecutionLog::getCreateTime, startTime);
        if (endTime != null) wrapper.le(RuleExecutionLog::getCreateTime, endTime);
        wrapper.orderByDesc(RuleExecutionLog::getCreateTime);
        return R.ok(logService.page(new Page<>(pageNum, pageSize), wrapper));
    }
}
