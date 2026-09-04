package com.bjjw.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.bjjw.rule.core.engine.QLExpressEngine;
import com.bjjw.rule.core.function.AggregateBuiltinFunctionRegistry;
import com.bjjw.rule.model.constant.RuleCompIds;
import com.bjjw.rule.model.dto.RuleResult;
import com.bjjw.rule.model.entity.*;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
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

    /**
     * 管理端试跑：按作用域读取已编译内容后执行
     *
     * @param definitionId 定义 ID
     * @param scopeCompId  设计内容作用域，空视为全国 0
     * @param params       入参
     * @param businessId   业务主键ID，用于关联具体业务记录
     */
    public RuleResult testExecute(Long definitionId, String scopeCompId, Map<String, Object> params, String businessId) {
        RuleDefinition definition = definitionService.getById(definitionId);
        if (definition == null) {
            RuleResult r = new RuleResult();
            r.setSuccess(false);
            r.setErrorMessage("规则定义不存在");
            return r;
        }

        RuleDefinitionContent content = definitionService.getContent(definitionId, RuleCompIds.normalize(scopeCompId));
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
        // 试跑精度与生产一致：取规则定义的高精度配置
        boolean precise = definition.getPreciseMode() != null && definition.getPreciseMode() == 1;
        // 试跑超时与生产一致：取规则定义的执行超时配置（0 表示不限制）
        long timeout = definition.getTimeoutMillis() != null ? definition.getTimeoutMillis() : 0L;
        RuleResult result = qlExpressEngine.execute(fullScript, params, true, precise, timeout);

        RuleExecutionLog log = new RuleExecutionLog();
        log.setRuleCode(definition.getRuleCode());
        if (definition.getProjectId() != null) {
            RuleProject project = projectService.getById(definition.getProjectId());
            if (project != null) {
                log.setProjectCode(project.getProjectCode());
            }
        }
        log.setRuleVersion(content.getCurrentVersion());
        log.setModelType(content.getModelType());
        log.setSource("SERVER");
        log.setCompId(RuleCompIds.normalize(scopeCompId));
        log.setBusinessId(businessId);
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
