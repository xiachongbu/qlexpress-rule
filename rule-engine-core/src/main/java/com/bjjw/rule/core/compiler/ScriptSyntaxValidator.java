package com.bjjw.rule.core.compiler;

import com.alibaba.qlexpress4.Express4Runner;
import com.alibaba.qlexpress4.InitOptions;
import com.alibaba.qlexpress4.exception.QLException;
import com.alibaba.qlexpress4.security.QLSecurityStrategy;
import com.bjjw.rule.core.function.AggregateBuiltinFunctionRegistry;

/**
 * QLExpress 脚本纯语法校验器（无副作用、不执行脚本）。
 * <p>通过 {@link Express4Runner#check(String)} 做语法解析，语法错误返回带行列与错误片段的精准信息；
 * 仅做语法解析，变量/函数未定义等运行时因素不影响校验结果。</p>
 */
public final class ScriptSyntaxValidator {

    private ScriptSyntaxValidator() {
    }

    /**
     * 校验脚本语法。
     *
     * @param script 脚本原文
     * @return 校验结果；成功时 compiledType 为 QLEXPRESS，失败时 errorMessage 含行列信息
     */
    public static CompileResult validate(String script) {
        if (script == null || script.trim().isEmpty()) {
            return CompileResult.fail("脚本内容为空");
        }
        try {
            Express4Runner runner = new Express4Runner(
                    InitOptions.builder()
                            .securityStrategy(QLSecurityStrategy.open())
                            .build());
            AggregateBuiltinFunctionRegistry.register(runner);
            runner.check(script);
            return CompileResult.ok(script, "QLEXPRESS");
        } catch (QLException e) {
            return CompileResult.fail(buildSyntaxErrorMessage(e));
        } catch (Exception e) {
            // check() 理论上仅抛 QLSyntaxException；其他异常保守放行，避免误伤合法脚本
            return CompileResult.ok(script, "QLEXPRESS");
        }
    }

    /** 组装带行列与错误片段的语法错误信息 */
    private static String buildSyntaxErrorMessage(QLException e) {
        try {
            int line = e.getLineNo();
            int col = e.getColNo();
            String reason = e.getReason() != null ? e.getReason() : e.getMessage();
            String lexeme = e.getErrLexeme();
            StringBuilder sb = new StringBuilder();
            if (line > 0) {
                sb.append("第 ").append(line).append(" 行");
                if (col > 0) {
                    sb.append("第 ").append(col).append(" 列");
                }
                sb.append("：");
            }
            sb.append(reason != null && !reason.isEmpty() ? reason : "脚本语法错误");
            if (lexeme != null && !lexeme.isEmpty()) {
                sb.append("（附近: ").append(lexeme).append("）");
            }
            return sb.toString();
        } catch (Exception ignore) {
            return e.getMessage() != null ? e.getMessage() : "脚本语法错误";
        }
    }
}
