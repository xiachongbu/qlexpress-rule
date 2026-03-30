package com.bjjw.rule.core.compiler;

import lombok.Data;

@Data
public class CompileResult {
    private boolean success;
    private String compiledScript;
    private String compiledType;
    private String errorMessage;

    public static CompileResult ok(String script, String type) {
        CompileResult r = new CompileResult();
        r.setSuccess(true);
        r.setCompiledScript(script);
        r.setCompiledType(type);
        return r;
    }

    public static CompileResult fail(String error) {
        CompileResult r = new CompileResult();
        r.setSuccess(false);
        r.setErrorMessage(error);
        return r;
    }
}
