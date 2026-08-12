package com.bjjw.rule.core.function;

import com.alibaba.fastjson.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 内置 HTTP 调用函数实现。
 *
 * <p>面向风控 KYB 等需要在规则/决策流动作节点中发起线上接口调用的场景（如商户进件、
 * 工商信息核验、黑名单查询）。基于 JDK 原生 {@link HttpURLConnection} 实现，不引入额外
 * HTTP 客户端依赖，与 core 模块保持零外部依赖。</p>
 *
 * <p>暴露给 QLExpress 脚本的函数（经 {@link AggregateBuiltinFunctionRegistry} 注册）：</p>
 * <ul>
 *   <li>{@code httpCall(config)}：全能形态，config 为 Map，支持 url/method/headers/body/
 *       connectTimeout/readTimeout 等键</li>
 *   <li>{@code httpGet(url, headers)}：GET 便捷形态</li>
 *   <li>{@code httpPost(url, body, headers)}：POST 便捷形态</li>
 * </ul>
 *
 * <p><b>返回约定</b>：函数定位是「拿到结构化数据」，复杂加工应在外部完成后再传入脚本。返回值分两种：</p>
 * <ul>
 *   <li><b>成功（2xx 且响应为 JSON）</b>：直接返回解析后的业务 JSON——对象为 {@link Map}、数组为 List，
 *       脚本可直接以 {@code resp.data.riskLevel}、{@code resp.code} 短路径访问。</li>
 *   <li><b>成功但空响应（含 204）</b>：返回 {@code {success:true, empty:true}}。</li>
 *   <li><b>失败</b>：不抛异常，返回错误标识 Map {@code {success:false, status, error}}，由脚本判断处理
 *       （如 {@code if (resp.success == false) { ... }}）。失败含：url 缺失、host 被拦、连接/超时/IO 错误、
 *       非 2xx、响应非 JSON。</li>
 * </ul>
 * <p>注意：成功返回的业务 JSON 通常不含 {@code success} 字段，脚本用 {@code resp.success == false} 判失败最稳妥
 * （成功时该字段为 null，条件不成立）。若业务响应自身带了 {@code success} 字段，以业务语义为准，此时可改用 HTTP 层无关的业务码判断。</p>
 *
 * <p><b>执行线程与超时</b>：HTTP 调用发生在 QLExpress 脚本执行线程内（同步阻塞），
 * 因此内置强制默认超时（连接 {@value #DEFAULT_CONNECT_TIMEOUT_MS}ms、读取
 * {@value #DEFAULT_READ_TIMEOUT_MS}ms），避免慢接口拖垮规则执行线程池；可按需在 config 覆盖。</p>
 *
 * <p><b>SSRF 防护</b>：默认放开（KYB 场景常调用内网核验服务）。如需限制，可在 SDK 侧通过
 * {@code rule-engine.client.allow-hosts} 配置 host 白名单，由 {@link #configureAllowHosts(Collection)} 注入；
 * 开启后 host 不在白名单则返回错误 Map，不发起连接。</p>
 */
public class HttpBuiltinFunctions {

    private static final Logger log = LoggerFactory.getLogger(HttpBuiltinFunctions.class);

    /** 默认连接超时（毫秒） */
    static final int DEFAULT_CONNECT_TIMEOUT_MS = 3000;
    /** 默认读取超时（毫秒） */
    static final int DEFAULT_READ_TIMEOUT_MS = 5000;
    /** 硬上限：单次调用超时不允许超过该值，避免误配置成超长阻塞 */
    static final int MAX_TIMEOUT_MS = 60000;

    /**
     * 由配置文件注入的 host 白名单（小写去重）。为 {@code null} 或空表示不启用白名单校验（放行所有 host）。
     * 声明为 volatile 以便注入线程与执行线程间可见。
     */
    private static volatile Set<String> allowHostsFromConfig;

    /**
     * 注入 host 白名单（由 SDK 在 Spring 启动时根据 {@code rule-engine.client.allow-hosts} 调用）。
     * <p>传入 null 或空集合表示不启用白名单（放行所有 host）。host 大小写不敏感。</p>
     *
     * @param hosts 允许访问的 host 集合
     */
    public static void configureAllowHosts(Collection<String> hosts) {
        if (hosts == null || hosts.isEmpty()) {
            allowHostsFromConfig = null;
            return;
        }
        Set<String> set = new LinkedHashSet<>();
        for (String h : hosts) {
            if (h != null && !h.trim().isEmpty()) {
                set.add(h.trim().toLowerCase());
            }
        }
        allowHostsFromConfig = set.isEmpty() ? null : set;
    }

    /**
     * 全能 HTTP 调用。
     *
     * @param config 请求配置 Map，支持键：
     *               <ul>
     *                 <li>url（String，必填）</li>
     *                 <li>method（String，默认 GET）</li>
     *                 <li>headers（Map，可选）</li>
     *                 <li>body（String 或 Map/其他对象，Map/对象将被 JSON 序列化，可选）</li>
     *                 <li>connectTimeout / readTimeout（数字毫秒，可选）</li>
     *               </ul>
     * @return 成功为业务 JSON（{@link Map}/List）或空响应标识 {@code {success:true,empty:true}}；失败为 {@code {success:false,status,error}}
     */
    public Object httpCall(Object config) {
        if (!(config instanceof Map)) {
            return errorMap(0, "httpCall 需要 Map 配置参数（含 url 等）");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> cfg = (Map<String, Object>) config;

        String url = asString(cfg.get("url"));
        if (url == null || url.trim().isEmpty()) {
            return errorMap(0, "httpCall 的 url 不能为空");
        }
        url = url.trim();
        String method = asString(cfg.get("method"));
        method = (method == null || method.trim().isEmpty()) ? "GET" : method.trim().toUpperCase();

        String hostDenyReason = checkHostAllowed(url);
        if (hostDenyReason != null) {
            return errorMap(0, hostDenyReason);
        }

        int connectTimeout = clampTimeout(cfg.get("connectTimeout"), DEFAULT_CONNECT_TIMEOUT_MS);
        int readTimeout = clampTimeout(cfg.get("readTimeout"), DEFAULT_READ_TIMEOUT_MS);
        String body = resolveBody(cfg.get("body"));

        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(connectTimeout);
            conn.setReadTimeout(readTimeout);
            conn.setRequestMethod(method);
            conn.setInstanceFollowRedirects(true);

            applyHeaders(conn, cfg.get("headers"));

            if (body != null && !"GET".equals(method) && !"HEAD".equals(method)) {
                if (conn.getRequestProperty("Content-Type") == null) {
                    conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
                }
                conn.setDoOutput(true);
                byte[] payload = body.getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload);
                    os.flush();
                }
            }

            int status = conn.getResponseCode();
            String respText = readResponse(conn, status);

            if (status < 200 || status >= 300) {
                return errorMap(status, "HTTP " + status + " : " + brief(respText));
            }
            // 空响应（含 204）视为成功但无数据，返回 {success:true, empty:true}
            if (respText == null || respText.trim().isEmpty()) {
                Map<String, Object> empty = new LinkedHashMap<>();
                empty.put("success", true);
                empty.put("empty", true);
                return empty;
            }
            Object json = tryParseJson(respText);
            if (json == null) {
                return errorMap(status, "HTTP 响应非 JSON : " + brief(respText));
            }
            return json;
        } catch (Exception e) {
            log.warn("[HttpBuiltinFunctions] 调用失败 url={} method={} err={}", url, method, e.getMessage());
            return errorMap(0, e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /** 构造失败标识 Map：{success:false, status, error}，交由脚本判断处理 */
    private static Map<String, Object> errorMap(int status, String error) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", false);
        m.put("status", status);
        m.put("error", error);
        return m;
    }

    /**
     * GET 便捷形态。
     *
     * @param url     请求地址
     * @param headers 请求头 Map（可为 null）
     * @return 同 {@link #httpCall(Object)}
     */
    public Object httpGet(Object url, Object headers) {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("url", url);
        cfg.put("method", "GET");
        cfg.put("headers", headers);
        return httpCall(cfg);
    }

    /**
     * POST 便捷形态。
     *
     * @param url     请求地址
     * @param body    请求体（String 或 Map/对象，Map/对象自动 JSON 序列化）
     * @param headers 请求头 Map（可为 null）
     * @return 同 {@link #httpCall(Object)}
     */
    public Object httpPost(Object url, Object body, Object headers) {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("url", url);
        cfg.put("method", "POST");
        cfg.put("body", body);
        cfg.put("headers", headers);
        return httpCall(cfg);
    }

    /** 应用请求头；仅接受 Map 形态，键值转字符串 */
    private static void applyHeaders(HttpURLConnection conn, Object headers) {
        if (!(headers instanceof Map)) {
            return;
        }
        for (Map.Entry<?, ?> e : ((Map<?, ?>) headers).entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            conn.setRequestProperty(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
        }
    }

    /** 请求体归一化：null 透传；String 原样；其余对象 JSON 序列化 */
    private static String resolveBody(Object body) {
        if (body == null) {
            return null;
        }
        if (body instanceof String) {
            return (String) body;
        }
        try {
            return JSON.toJSONString(body);
        } catch (Exception e) {
            return String.valueOf(body);
        }
    }

    /** 读取响应体：2xx/3xx 读 inputStream，否则读 errorStream */
    private static String readResponse(HttpURLConnection conn, int status) {
        try {
            InputStream is = (status >= 200 && status < 400) ? conn.getInputStream() : conn.getErrorStream();
            if (is == null) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                char[] buf = new char[4096];
                int n;
                while ((n = reader.read(buf)) != -1) {
                    sb.append(buf, 0, n);
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** 尝试将响应体解析为 JSON 对象/数组；非 JSON（不以 {、[ 开头）或解析失败返回 null */
    private static Object tryParseJson(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        String t = text.trim();
        if (t.charAt(0) != '{' && t.charAt(0) != '[') {
            return null;
        }
        try {
            return JSON.parse(t);
        } catch (Exception e) {
            return null;
        }
    }

    /** 截取响应片段用于异常信息，避免超长 body 污染日志 */
    private static String brief(String text) {
        if (text == null) {
            return "";
        }
        String t = text.trim();
        return t.length() > 200 ? t.substring(0, 200) + "..." : t;
    }

    /** 超时值归一化：非法/缺失回退默认值，并夹紧到 [1, MAX_TIMEOUT_MS] */
    private static int clampTimeout(Object raw, int defaultMs) {
        int v = defaultMs;
        if (raw instanceof Number) {
            v = ((Number) raw).intValue();
        } else if (raw instanceof String) {
            try {
                v = Integer.parseInt(((String) raw).trim());
            } catch (NumberFormatException ignored) {
                v = defaultMs;
            }
        }
        if (v <= 0) {
            v = defaultMs;
        }
        return Math.min(v, MAX_TIMEOUT_MS);
    }

    /**
     * host 白名单校验。白名单（{@link #allowHostsFromConfig}）未配置时不限制，返回 null（放行）；
     * 配置后 host 不在白名单则返回拒绝原因。
     */
    private static String checkHostAllowed(String url) {
        Set<String> allow = allowHostsFromConfig;
        if (allow == null || allow.isEmpty()) {
            return null;
        }
        String host;
        try {
            host = new URL(url.trim()).getHost();
        } catch (Exception e) {
            return "非法 url: " + url;
        }
        if (host == null) {
            return "无法解析 url 的 host: " + url;
        }
        if (allow.contains(host.toLowerCase())) {
            return null;
        }
        return "目标 host 不在白名单(rule-engine.client.allow-hosts): " + host;
    }

    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
