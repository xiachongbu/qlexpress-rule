package com.bjjw.rule.core.engine;

import com.alibaba.qlexpress4.Express4Runner;
import com.alibaba.qlexpress4.InitOptions;

public class QLExpressEngineFactory {

    private static volatile Express4Runner instance;

    public static Express4Runner getInstance() {
        if (instance == null) {
            synchronized (QLExpressEngineFactory.class) {
                if (instance == null) {
                    InitOptions options = InitOptions.builder()
                            .traceExpression(true)
                            .build();
                    instance = new Express4Runner(options);
                }
            }
        }
        return instance;
    }

    public static Express4Runner createRunner(InitOptions options) {
        return new Express4Runner(options);
    }
}
