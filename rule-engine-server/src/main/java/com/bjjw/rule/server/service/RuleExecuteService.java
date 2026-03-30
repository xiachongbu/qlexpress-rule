package com.bjjw.rule.server.service;

import com.bjjw.rule.core.function.AggregateBuiltinFunctionRegistry;
import com.bjjw.rule.core.engine.QLExpressEngine;
import com.bjjw.rule.model.dto.RuleResult;
import com.bjjw.rule.model.entity.RuleDefinition;
import com.bjjw.rule.model.entity.RuleDefinitionContent;
import com.bjjw.rule.model.entity.RuleExecutionLog;
import com.bjjw.rule.model.entity.RuleFunction;
import com.bjjw.rule.model.entity.RuleProject;
import com.alibaba.fastjson.JSON;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RuleExecuteService {

    @Resource
    private QLExpressEngine qlExpressEngine;

    @Resource
    private RuleDefinitionService definitionService;

    @Resource
    private RuleProjectService projectService;

    @Resource
    private RuleExecutionLogService logService;

    @Resource
    private RuleFunctionService functionService;

    @Resource
    private FunctionRegistrar functionRegistrar;

    public RuleResult testExecute(Long definitionId, Map<String, Object> params) {
        RuleDefinition definition = definitionService.getById(definitionId);
        if (definition == null) {
            RuleResult r = new RuleResult();
            r.setSuccess(false);
            r.setErrorMessage("规则定义不存在");
            return r;
        }

        RuleDefinitionContent content = definitionService.getContent(definitionId);
        if (content == null || content.getCompileStatus() != 1) {
            RuleResult r = new RuleResult();
            r.setSuccess(false);
            r.setErrorMessage("规则尚未编译成功，请先编译");
            return r;
        }

        // 加载项目自定义函数并注册到引擎
        List<RuleFunction> allFuncs = functionService.listByProject(definition.getProjectId());
        List<RuleFunction> scriptFuncs = allFuncs.stream()
                .filter(f -> "SCRIPT".equals(f.getImplType())).collect(Collectors.toList());
        List<RuleFunction> javaFuncs = allFuncs.stream()
                .filter(f -> "JAVA".equals(f.getImplType())).collect(Collectors.toList());
        List<RuleFunction> beanFuncs = allFuncs.stream()
                .filter(f -> "BEAN".equals(f.getImplType())).collect(Collectors.toList());

        String funcPrefix = functionRegistrar.buildScriptFunctionPrefix(scriptFuncs);
        functionRegistrar.registerJavaFunctions(javaFuncs, qlExpressEngine.getRunner());
        functionRegistrar.registerBeanFunctions(beanFuncs, qlExpressEngine.getRunner());
        AggregateBuiltinFunctionRegistry.register(qlExpressEngine.getRunner());

        String fullScript = funcPrefix.isEmpty()
                ? content.getCompiledScript()
                : funcPrefix + "\n" + content.getCompiledScript();
        RuleResult result = qlExpressEngine.execute(fullScript, params, true);

        RuleExecutionLog log = new RuleExecutionLog();
        log.setRuleCode(definition.getRuleCode());
        if (definition.getProjectId() != null) {
            RuleProject project = projectService.getById(definition.getProjectId());
            if (project != null) {
                log.setProjectCode(project.getProjectCode());
            }
        }
        log.setRuleVersion(definition.getCurrentVersion());
        log.setModelType(definition.getModelType());
        log.setSource("SERVER");
        log.setInputParams(JSON.toJSONString(params));
        log.setOutputResult(JSON.toJSONString(result.getResult()));
        log.setSuccess(result.isSuccess() ? 1 : 0);
        log.setErrorMessage(result.getErrorMessage());
        log.setExecuteTimeMs(result.getExecuteTimeMs());
        if (result.getTraces() != null) {
            log.setTraceInfo(JSON.toJSONString(result.getTraces()));
        }
        logService.save(log);

        return result;
    }
}
