package com.bjjw.rule.core.function;

import com.alibaba.qlexpress4.Express4Runner;

/**
 * 将内置聚合函数注册到 {@link Express4Runner}，使用 addOrReplace 语义以便重复调用。
 */
public final class AggregateBuiltinFunctionRegistry {

    private static final AggregateBuiltinFunctions DELEGATE = new AggregateBuiltinFunctions();
    private static final Class<?>[] SINGLE_OBJECT = {Object.class};

    private AggregateBuiltinFunctionRegistry() {
    }

    /**
     * 注册 sum、count、max、min、avg；同名已存在则覆盖。
     *
     * @param runner QLExpress 执行器
     */
    public static void register(Express4Runner runner) {
        if (runner == null) {
            return;
        }
        runner.addFunctionOfServiceMethod("sum", DELEGATE, "sum", SINGLE_OBJECT);
        runner.addFunctionOfServiceMethod("count", DELEGATE, "count", SINGLE_OBJECT);
        runner.addFunctionOfServiceMethod("max", DELEGATE, "max", SINGLE_OBJECT);
        runner.addFunctionOfServiceMethod("min", DELEGATE, "min", SINGLE_OBJECT);
        runner.addFunctionOfServiceMethod("avg", DELEGATE, "avg", SINGLE_OBJECT);
    }
}
