package com.bjjw.rule.client.log;

import com.bjjw.rule.model.entity.RuleExecutionLog;
import java.util.List;

public class NoOpLogReporter implements ExecutionLogReporter {
    @Override
    public void report(List<RuleExecutionLog> logs) {
        // intentionally empty
    }
}
