package com.bjjw.rule.server.controller.mgmt;

import com.bjjw.rule.model.dto.mgmt.RuleExecutionLogListRequest;
import com.bjjw.rule.model.entity.RuleExecutionLog;
import com.bjjw.rule.server.common.R;
import com.bjjw.rule.server.service.RuleExecutionLogService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/rule/log")
public class ExecutionLogController {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private RuleExecutionLogService logService;

    /**
     * 执行日志分页（POST + JSON），非管理员按 comp_id 过滤
     */
    @PostMapping("/list")
    public R<IPage<RuleExecutionLog>> list(@RequestBody RuleExecutionLogListRequest req) {
        int pageNum = req.getPageNum() == null ? 1 : req.getPageNum();
        int pageSize = req.getPageSize() == null ? 20 : req.getPageSize();
        LambdaQueryWrapper<RuleExecutionLog> wrapper = new LambdaQueryWrapper<>();
        if (req.getSource() != null && !req.getSource().isEmpty()) {
            wrapper.eq(RuleExecutionLog::getSource, req.getSource());
        }
        if (req.getProjectCode() != null && !req.getProjectCode().isEmpty()) {
            wrapper.eq(RuleExecutionLog::getProjectCode, req.getProjectCode());
        }
        if (req.getRuleCode() != null && !req.getRuleCode().isEmpty()) {
            wrapper.eq(RuleExecutionLog::getRuleCode, req.getRuleCode());
        }
        if (req.getBusinessId() != null && !req.getBusinessId().isEmpty()) {
            wrapper.eq(RuleExecutionLog::getBusinessId, req.getBusinessId());
        }
        LocalDateTime startTime = parseDateTime(req.getStartTime());
        LocalDateTime endTime = parseDateTime(req.getEndTime());
        if (startTime != null) {
            wrapper.ge(RuleExecutionLog::getCreateTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(RuleExecutionLog::getCreateTime, endTime);
        }

        List<String> allowedCompIds = getAllowedCompIds();
        if (allowedCompIds != null) {
            wrapper.in(RuleExecutionLog::getCompId, allowedCompIds);
        }

        // 列表查询排除大字段，避免返回过多数据
        wrapper.select(RuleExecutionLog.class, info ->
                !info.getColumn().equals("input_params")
                && !info.getColumn().equals("output_result")
                && !info.getColumn().equals("trace_info"));

        wrapper.orderByDesc(RuleExecutionLog::getCreateTime);
        return R.ok(logService.page(new Page<>(pageNum, pageSize), wrapper));
    }

    /**
     * 根据 ID 获取执行日志完整详情（含 input_params、output_result、trace_info）
     */
    @GetMapping("/detail/{id}")
    public R<RuleExecutionLog> detail(@PathVariable Long id) {
        RuleExecutionLog log = logService.getById(id);
        if (log == null) {
            return R.fail("日志不存在");
        }
        // 非管理员校验数据权限
        List<String> allowedCompIds = getAllowedCompIds();
        if (allowedCompIds != null && !allowedCompIds.contains(log.getCompId())) {
            return R.fail("无权限查看该日志");
        }
        return R.ok(log);
    }

    /**
     * 作用域过滤扩展点：开源版不做登录用户驱动的过滤，返回 null 表示不过滤全部作用域。
     * 如需按作用域/租户隔离，可在此接入自定义的当前用户上下文。
     */
    private List<String> getAllowedCompIds() {
        return null;
    }

    private static LocalDateTime parseDateTime(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        return LocalDateTime.parse(raw, DT_FMT);
    }
}
