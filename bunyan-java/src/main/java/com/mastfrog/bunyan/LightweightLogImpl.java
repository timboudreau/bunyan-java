/*
 * The MIT License
 *
 * Copyright 2017 Tim Boudreau.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.mastfrog.bunyan;

import static com.mastfrog.bunyan.LogImpl.ISO_FORMAT;
import static com.mastfrog.bunyan.LogImpl.formattedNow;
import static com.mastfrog.bunyan.LogImpl.pid;
import com.mastfrog.bunyan.type.LogLevel;
import com.mastfrog.util.Checks;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Array;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.joda.time.DateTime;

/**
 * Pending:  Optimize me.
 *
 * @author Tim Boudreau
 */
final class LightweightLogImpl<T extends LogLevel> implements Log<T> {

    private final String name;

    private final T level;
    private final List<Object> m = new LinkedList<>();
    private final LogSink sink;
    private final LoggingConfig config;
    private String msg = "";
    private int last = -1;
    private static final int INCREMENT = 10;
    private String[] keys = new String[INCREMENT];
    private Object[] vals = new Object[INCREMENT];

    static final List<String> FORBIDDEN = Arrays.asList("hostname", "level", "msg", "pid", "time", "v");

    LightweightLogImpl(String name, T level, LogSink sink, LoggingConfig config) {
        if (!(sink instanceof DefaultLogSink)) {
            throw new AssertionError("Not a DefaultLogSink");
        }
        this.name = name;
        this.level = level;
        this.sink = sink;
        this.config = config;
    }
    
    LogImpl<T> toPlainImpl() {
        LogImpl<T> li = new LogImpl<>(name, level, sink, config);
        for (int i = 0; i < keys.length; i++) {
            li.add(keys[i], vals[i]);
        }
        return li;
    }

    @Override
    public T level() {
        return level;
    }

    @Override
    public Log<T> message(String msg) {
        if (msg != null) {
            this.msg = msg;
        }
        return this;
    }

    private void maybeGrow() {
        assert keys.length == vals.length;
        if (last == keys.length - 1) {
            String[] newKeys = new String[keys.length + INCREMENT];
            String[] newVals = new String[vals.length + INCREMENT];
            System.arraycopy(keys, 0, newKeys, 0, keys.length);
            System.arraycopy(vals, 0, newVals, 0, vals.length);
        }
    }

    @Override
    public Log<T> add(Object o) {
        checkValue(o);
        if ("".equals(msg) && o instanceof CharSequence) {
            msg = o.toString();
            return this;
        }
        if (o instanceof Map<?, ?>) {
            Map<?, ?> m1 = (Map<?, ?>) o;
            for (Map.Entry<?, ?> e : m1.entrySet()) {
                String key = e.getKey().toString();
                if (FORBIDDEN.contains(key)) {
                    key = "_" + key;
                }
                add(key, e.getValue());
            }
        }
        return this;
    }

    @Override
    public Log<T> add(String name, Object value) {
        Checks.notNull("name", name);
        checkValue(value);
        maybeGrow();
        keys[++last] = name;
        vals[last] = value;
        return this;
    }

    @Override
    public Log<T> addIfNotNull(String name, Object value) {
        if (value != null) {
            add(name, value);
        }
        return this;
    }

    @Override
    public Log<T> add(Throwable t) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintStream ps = new PrintStream(baos)) {
            t.printStackTrace(ps);
        }
        return add("error", new String(baos.toByteArray()));
    }

    private void checkValue(Object val) {
        if (true) {
            return;
        }
        if (val == null || val instanceof Number || val instanceof CharSequence || val instanceof Boolean) {
            return;
        }
        if (val instanceof Map<?, ?>) {
            for (Map.Entry<?, ?> e : ((Map<?, ?>) val).entrySet()) {
                checkValue(e.getValue());
            }
            return;
        }
        if (val.getClass().isArray()) {
            int max = Array.getLength(val);
            for (int i = 0; i < max; i++) {
                Object o = Array.get(val, i);
                checkValue(o);
            }
            return;
        }
        if (val instanceof List<?>) {
            List<?> l = (List<?>) val;
            for (Object o : l) {
                checkValue(o);
            }
            return;
        }
        throw new UnsupportedOperationException("Unsupported type " + val + " (" + val.getClass().getName() + ")");
    }

    @Override
    public void close() {
        StringBuilder sb = new StringBuilder(680).append('{');
        sb.append('"').append("name").append('"').append(':').append('"');
        toString(name, sb);
        sb.append(',').append("msg").append('"').append(':');
        toString(msg, sb);
        sb.append(",\"v\":0");
        sb.append(",\"time\":\"").append(formattedNow()).append("\",");
        sb.append("\"pid\":").append(pid()).append(',');
        sb.append("\"level\":").append(level.ordinal()).append(',');
        sb.append("\"hostname\":");
        toString(config.hostname(), sb);
        sb.append(',');
        for (int i = 0; i < keys.length; i++) {
            if (keys[i] == null) {
                break;
            }
            toString(keys[i], sb);
            sb.append(':');
            toString(vals[i], sb);
            if (i + 1 != keys.length && keys[i + 1] != null) {
                sb.append(',');
            }
        }
        sb.append("\n");
        ((DefaultLogSink) sink).rawWrite(sb.toString());
    }

    private static final DecimalFormat FMT = new DecimalFormat("##################.####################");

    private StringBuilder toString(Object o, StringBuilder sb) {
        if (o == null) {
            return sb.append("null");
        } else if (o instanceof Integer || o instanceof Short || o instanceof Byte || o instanceof Long || o instanceof Boolean) {
            return sb.append(o.toString());
        } else if (o instanceof Double) {
            return sb.append(FMT.format((Double) o));
        } else if (o instanceof Float) {
            return sb.append(FMT.format((Float) o));
        } else if (o instanceof Map<?, ?>) {
            Map<?, ?> m1 = (Map<?, ?>) o;
            sb.append('{');
            for (Iterator<? extends Map.Entry<?, ?>> it = m1.entrySet().iterator(); it.hasNext();) {
                Map.Entry<?, ?> e = it.next();
                toString(e.getKey().toString(), sb);
                sb.append(':');
                toString(e.getValue(), sb);
                if (it.hasNext()) {
                    sb.append(',');
                }
            }
            return sb.append('}');
        } else if (o instanceof List<?>) {
            sb.append('[');
            for (Iterator<?> it=((List<?>) o).iterator(); it.hasNext();) {
                toString(it.next(), sb);
                if (it.hasNext()) {
                    sb.append(',');
                }
            }
            return sb.append(']');
        } else if (o.getClass().isArray()) {
            int max = Array.getLength(o);
            sb.append('[');
            for (int i = 0; i < max; i++) {
                toString(Array.get(o, i), sb);
                if (i != max-1) {
                    sb.append(',');
                }
            }
            return sb.append(']');
        } else if (o instanceof DateTime) {
            DateTime dt = (DateTime) o;
            return sb.append('"').append(ISO_FORMAT.print(dt)).append('"');
        } else if (o instanceof Date) {
            DateTime dt = new DateTime(((Date)o).getTime());
            return toString(dt, sb);
        }
        String result;
        if (o instanceof String) {
            result = (String) o;
        } else if (o instanceof CharSequence) {
            result = o.toString();
        } else {
            throw new UnsupportedOperationException(o + "");
        }
        result = result.replaceAll("\\\\", "\\");
        result = result.replaceAll("\"", "\\\"");
        result = result.replaceAll("'", "\\'");
        result = result.replaceAll("\t", "\\t").replaceAll("\n", "\\n");
        return sb.append('"').append(result).append('"');
    }
}
