package com.bjjw.rule.core.function;

import com.alibaba.fastjson.JSONArray;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 内置聚合函数实现：对单层序列做 sum / count / max / min / avg。
 * <p>不展开嵌套集合；非容器标量仅当为 {@link Number} 时视为单元素序列。</p>
 */
public class AggregateBuiltinFunctions {

    /**
     * 对序列中的有效数字求和；跳过 null 与非数字元素。
     *
     * @param data 集合、数组、{@link JSONArray}、{@link Iterable}，或单个 {@link Number}
     * @return 无有效数字时返回 {@link BigDecimal#ZERO}
     */
    public BigDecimal sum(Object data) {
        BigDecimal acc = BigDecimal.ZERO;
        for (Object o : normalizeToElements(data)) {
            BigDecimal n = toBigDecimal(o);
            if (n != null) {
                acc = acc.add(n);
            }
        }
        return acc;
    }

    /**
     * 返回序列元素个数（含 null、非数字）；与数值无关。
     *
     * @param data 同 {@link #sum(Object)}
     * @return 元素个数，null 或无法识别的容器类型为 0；单个 {@link Number} 视为 1
     */
    public long count(Object data) {
        return normalizeToElements(data).size();
    }

    /**
     * 取序列中有效数字的最大值。
     *
     * @param data 同 {@link #sum(Object)}
     * @return 无有效数字时返回 null
     */
    public BigDecimal max(Object data) {
        BigDecimal best = null;
        for (Object o : normalizeToElements(data)) {
            BigDecimal n = toBigDecimal(o);
            if (n == null) {
                continue;
            }
            if (best == null || n.compareTo(best) > 0) {
                best = n;
            }
        }
        return best;
    }

    /**
     * 取序列中有效数字的最小值。
     *
     * @param data 同 {@link #sum(Object)}
     * @return 无有效数字时返回 null
     */
    public BigDecimal min(Object data) {
        BigDecimal best = null;
        for (Object o : normalizeToElements(data)) {
            BigDecimal n = toBigDecimal(o);
            if (n == null) {
                continue;
            }
            if (best == null || n.compareTo(best) < 0) {
                best = n;
            }
        }
        return best;
    }

    /**
     * 有效数字的算术平均值（sum / 有效数字个数）。
     *
     * @param data 同 {@link #sum(Object)}
     * @return 无有效数字时返回 null
     */
    public BigDecimal avg(Object data) {
        BigDecimal s = BigDecimal.ZERO;
        int c = 0;
        for (Object o : normalizeToElements(data)) {
            BigDecimal n = toBigDecimal(o);
            if (n != null) {
                s = s.add(n);
                c++;
            }
        }
        if (c == 0) {
            return null;
        }
        return s.divide(BigDecimal.valueOf(c), 10, RoundingMode.HALF_UP);
    }

    /**
     * 将入参规范为元素列表：null 为空；{@link Map} 不按值展开（视为无法识别，返回空）。
     */
    private static List<Object> normalizeToElements(Object data) {
        if (data == null) {
            return Collections.emptyList();
        }
        if (data instanceof JSONArray) {
            JSONArray arr = (JSONArray) data;
            List<Object> out = new ArrayList<>(arr.size());
            for (int i = 0; i < arr.size(); i++) {
                out.add(arr.get(i));
            }
            return out;
        }
        if (data instanceof Collection) {
            return new ArrayList<>((Collection<?>) data);
        }
        if (data instanceof Iterable && !(data instanceof Map)) {
            List<Object> out = new ArrayList<>();
            for (Object o : (Iterable<?>) data) {
                out.add(o);
            }
            return out;
        }
        if (data.getClass().isArray()) {
            int len = Array.getLength(data);
            List<Object> out = new ArrayList<>(len);
            for (int i = 0; i < len; i++) {
                out.add(Array.get(data, i));
            }
            return out;
        }
        if (data instanceof Number) {
            return Collections.singletonList(data);
        }
        return Collections.emptyList();
    }

    /**
     * 将元素转为 {@link BigDecimal}；非数字返回 null。
     */
    private static BigDecimal toBigDecimal(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof BigDecimal) {
            return (BigDecimal) o;
        }
        if (o instanceof BigInteger) {
            return new BigDecimal((BigInteger) o);
        }
        if (o instanceof Number) {
            Number n = (Number) o;
            if (o instanceof Byte || o instanceof Short || o instanceof Integer || o instanceof Long) {
                return BigDecimal.valueOf(n.longValue());
            }
            return BigDecimal.valueOf(n.doubleValue());
        }
        return null;
    }
}
