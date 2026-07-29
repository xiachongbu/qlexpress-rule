package com.bjjw.rule.server.common;

import lombok.Data;

@Data
public class R<T> {
    private int code;
    private String message;
    private T data;

    /**
     * 兼容前端统一拦截器：其读取错误字段为 msg。
     * 基于 Jackson getter 序列化，使返回体在保留 message 的同时额外输出 msg。
     */
    public String getMsg() {
        return message;
    }

    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.setCode(200);
        r.setMessage("success");
        r.setData(data);
        return r;
    }

    public static <T> R<T> ok() {
        return ok(null);
    }

    public static <T> R<T> fail(String message) {
        R<T> r = new R<>();
        r.setCode(500);
        r.setMessage(message);
        return r;
    }

    public static <T> R<T> fail(int code, String message) {
        R<T> r = new R<>();
        r.setCode(code);
        r.setMessage(message);
        return r;
    }
}
