package com.bjjw.rule.core.function;

import com.alibaba.qlexpress4.Express4Runner;

/**
 * 将内置函数注册到 {@link Express4Runner}，使用 addOrReplace 语义以便重复调用。
 * <p>含聚合函数（sum/count/max/min/avg）与 HTTP 调用函数（httpCall/httpGet/httpPost）。
 * 所有执行入口（服务端试跑、客户端生产执行、语法校验）均通过本类统一注册，
 * 因此内置函数无需在 rule_function 表逐条配置，也无需发布同步。</p>
 */
public final class AggregateBuiltinFunctionRegistry {

    private static final AggregateBuiltinFunctions DELEGATE = new AggregateBuiltinFunctions();
    private static final HttpBuiltinFunctions HTTP = new HttpBuiltinFunctions();
    private static final Class<?>[] SINGLE_OBJECT = {Object.class};
    private static final Class<?>[] TWO_OBJECT = {Object.class, Object.class};
    private static final Class<?>[] THREE_OBJECT = {Object.class, Object.class, Object.class};

    private AggregateBuiltinFunctionRegistry() {
    }

    /**
     * 注册 sum、count、max、min、avg 与 httpCall、httpGet、httpPost；同名已存在则覆盖。
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
        runner.addFunctionOfServiceMethod("httpCall", HTTP, "httpCall", SINGLE_OBJECT);
        runner.addFunctionOfServiceMethod("httpGet", HTTP, "httpGet", TWO_OBJECT);
        runner.addFunctionOfServiceMethod("httpPost", HTTP, "httpPost", THREE_OBJECT);
    }
}
