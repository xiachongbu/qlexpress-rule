package com.bjjw.rule.server.service;

import com.bjjw.rule.model.entity.RuleExecutionLog;
import com.bjjw.rule.server.mapper.RuleExecutionLogMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 规则执行日志服务，提供批量插入等能力
 */
@Service
public class RuleExecutionLogService extends ServiceImpl<RuleExecutionLogMapper, RuleExecutionLog> {
}
