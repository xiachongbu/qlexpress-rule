package com.bjjw.rule.server.service;

import com.bjjw.rule.core.compiler.*;
import com.bjjw.rule.model.constant.RuleCompIds;
import com.bjjw.rule.model.entity.RuleDefinition;
import com.bjjw.rule.model.entity.RuleDefinitionContent;
import com.bjjw.rule.model.entity.RuleVariable;
import com.bjjw.rule.server.mapper.RuleDefinitionContentMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class RuleCompileService {

    @Resource
    private RuleDefinitionService definitionService;

    @Resource
    private RuleDefinitionContentMapper contentMapper;

    @Resource
    private RuleVariableService variableService;

    /**
     * 编译指定定义下全国默认（0）作用域
     */
    public CompileResult compile(Long definitionId) {
        return compile(definitionId, RuleCompIds.NATIONAL);
    }

    /**
     * 编译指定定义与省份作用域下的设计内容
     *
     * @param definitionId 定义 ID
     * @param scopeCompId  作用域 compId，空视为全国
     */
    public CompileResult compile(Long definitionId, String scopeCompId) {
        RuleDefinition definition = definitionService.getById(definitionId);
        if (definition == null) {
            return CompileResult.fail("规则定义不存在");
        }

        RuleDefinitionContent content = definitionService.getContent(definitionId, scopeCompId);
        if (content == null) {
            return CompileResult.fail("规则内容不存在");
        }

        RuleCompiler compiler = RuleModelCompilers.get(content.getModelType());
        if (compiler == null) {
            return CompileResult.fail("暂不支持的模型类型: " + content.getModelType());
        }

        // 注入项目常量：编译器排除常量出输出集，并前置常量赋值序言，使常量在脚本中固化为其值
        List<RuleVariable> vars = definition.getProjectId() != null
                ? variableService.listByProject(definition.getProjectId())
                : null;
        LinkedHashSet<String> constantNames = ConstantPrefixBuilder.constantNames(vars);
        String constantPrefix = ConstantPrefixBuilder.build(vars);

        CompileResult result = compiler.compile(content.getModelJson(), constantNames, constantPrefix);

        // 脚本类型：直通编译不含深层语法解析，追加 QLExpress check() 解析校验（括号预检已在直通编译器内完成）
        if (result.isSuccess() && "SCRIPT".equals(content.getModelType())) {
            CompileResult syntax = validateScript(result.getCompiledScript());
            if (!syntax.isSuccess()) {
                result = syntax;
            }
        }

        content.setCompileStatus(result.isSuccess() ? 1 : 2);
        content.setCompiledScript(result.getCompiledScript());
        content.setCompiledType(result.getCompiledType());
        content.setCompileMessage(result.isSuccess() ? null : result.getErrorMessage());
        content.setCompileTime(LocalDateTime.now());
        if (result.isSuccess()) {
            content.setScriptMode("visual");
        }
        contentMapper.updateById(content);

        return result;
    }

    /**
     * 验证手写脚本语法（不覆盖可视化模型编译结果）。
     * 委托给 {@link ScriptSyntaxValidator}，以 QLExpress check() 做纯语法解析校验。
     */
    public CompileResult validateScript(String script) {
        return ScriptSyntaxValidator.validate(script);
    }
}
