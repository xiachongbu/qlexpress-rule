package com.bjjw.rule.client.log;

import com.bjjw.rule.model.entity.RuleExecutionLog;
import java.util.List;

public interface ExecutionLogReporter {
    void report(List<RuleExecutionLog> logs);
}
