/*
 * The MIT License
 *
 * Copyright 2015 Tim Boudreau.
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

import com.mastfrog.bunyan.type.LogLevel;
import com.mastfrog.util.Checks;
import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.collections.MapBuilder2;
import com.mastfrog.util.strings.AppendableCharSequence;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Tim Boudreau
 */
class LogImpl<T extends LogLevel> implements Log<T> {

    public static final DateTimeFormatter ISO_INSTANT;

    static {
        ISO_INSTANT = new DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .appendInstant()
                .toFormatter(Locale.US);
    }

    private final String name;

    private final T level;
    private final List<Object> m = new LinkedList<>();
    private final LogSink sink;
    private final LoggingConfig config;

    LogImpl(String name, T level, LogSink sink, LoggingConfig config) {
        this.name = name;
        this.level = level;
        this.sink = sink;
        this.config = config;
    }

    @Override
    public T level() {
        return level;
    }

    @Override
    public Log<T> message(String msg) {
        Checks.notNull("msg", msg);
        m.add(Collections.singletonMap("msg", msg));
        return this;
    }

    @Override
    public Log<T> add(Object object) {
        Checks.notNull("object", object);
        m.add(object);
        return this;
    }

    @Override
    public Log<T> add(String name, Object value) {
        Checks.notNull("name", name);
        m.add(Collections.singletonMap(name, value));
        return this;
    }

    @Override
    public Log<T> add(Throwable error) {
        Checks.notNull("error", error);
        m.add(Collections.singletonMap("error", error));
        String msg = error.getMessage();
        if (msg != null) {
            m.add(Collections.singletonMap("msg", msg));
        }
        return this;
    }

    static String formattedNow() {
        ZonedDateTime now = ZonedDateTime.now().withZoneSameInstant(ZoneId.of("GMT"));
        return ISO_INSTANT.format(now);
    }

    static int pid = -1;

    static int pid() {
        if (pid != -1) {
            // Getting the pid is a high-cost call if a bunch of threads
            // wind up locked in InetAddress.getLocalHost().
            return pid;
        }
        String beanName = ManagementFactory.getRuntimeMXBean().getName();
        try {
            return pid = Integer.parseInt(beanName.split("@")[0]);
        } catch (NumberFormatException nfe) {
            System.err.println("Could not find pid in '" + beanName + "'");
            return pid = 0;
        }
    }

    @Override
    public void close() {
        if (!level.isEnabled()) {
            return;
        }
        AppendableCharSequence msg = new AppendableCharSequence(60);
        List<Object> stuff = new LinkedList<>(m);
        MapBuilder2<String, Object> mb = CollectionUtils.map();
        for (Iterator<Object> it = stuff.iterator(); it.hasNext();) {
            Object o = it.next();
            CharSequence s = null;
            if (o == null) {
                continue;
            } else if (o instanceof CharSequence) {
                s = (CharSequence) o;
                it.remove();
            } else if (o instanceof Boolean || o instanceof Number) {
                s = o.toString();
            } else if (o instanceof Map) {
                Map<?, ?> m = ((Map) o);
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    if ("msg".equals(e.getKey())) {
                        Object ob = e.getValue();
                        if (ob instanceof CharSequence) {
                            s = (CharSequence) ob;
                        } else {
                            mb.map("_msg").to(ob);
                        }
                    } else {
                        Object key = e.getKey();
                        mb.map(Objects.toString(key)).to(e.getValue());
                    }
                }

                it.remove();
            } else if (o instanceof List) {
                List<?> l = (List<?>) o;
                int sz = l.size();
                for (int i = 0; i < sz; i++) {
                    mb.map(Integer.toString(i)).to(l.get(i));
                }
            } else {
                try {
                    Map<?, ?> mm = config.mapper().readValue(config.mapper().writeValueAsBytes(o), Map.class);
                    for (Map.Entry<?, ?> e : mm.entrySet()) {
                        mb.map(Objects.toString(e.getKey())).to(e.getValue());
                    }
                } catch (IOException ex) {
                    Logger.getLogger(LogImpl.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            if (o instanceof Map) {
                Map<?, ?> m = (Map) o;
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    if (!"msg".equals(e.getKey())) {
                        mb.map(Objects.toString(e.getKey())).to(e.getValue());
                    }
                }
            }
            if (s != null) {
                if (msg.length() != 0) {
                    msg.append(' ');
                }
                msg.append(s);
            }
        }

        mb.map("name").to(name)
                .map("msg").to(msg)
                .map("v").to(0)
                .map("time").to(formattedNow())
                .map("pid").to(pid())
                .map("level").to(level.ordinal())
                .map("hostname").to(config.hostname());
        sink.push(level, mb.build());
    }

    @Override
    public Log<T> addIfNotNull(String name, Object value) {
        if (value != null) {
            return add(name, value);
        }
        return this;
    }

    public String toString() {
        return super.toString() + "{sink=" + sink + "}";
    }
}
