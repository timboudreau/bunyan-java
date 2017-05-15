package com.mastfrog.bunyan;

import com.mastfrog.bunyan.type.LogLevel;
import com.mastfrog.util.Checks;
import com.mastfrog.util.collections.MapBuilder;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

/**
 *
 * @author Tim Boudreau
 */
class LogImpl<T extends LogLevel> implements Log<T> {

    private final String name;

    private final T level;
    private final List<Object> m = new LinkedList<>();
    private final LogSink sink;
    private final LoggingConfig config;

    public LogImpl(String name, T level, LogSink sink, LoggingConfig config) {
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
        if (!level.isEnabled()) {
            return this;
        }
        m.add(Collections.singletonMap("msg", msg));
        return this;
    }

    @Override
    public Log<T> add(Object object) {
        Checks.notNull("object", object);
        if (!level.isEnabled()) {
            return this;
        }
        m.add(object);
        return this;
    }

    @Override
    public Log<T> add(String name, Object value) {
        Checks.notNull("name", name);
        if (!level.isEnabled()) {
            return this;
        }
        m.add(Collections.singletonMap(name, value));
        return this;
    }

    @Override
    public Log<T> add(Throwable error) {
        Checks.notNull("error", error);
        if (!level.isEnabled()) {
            return this;
        }
        m.add(Collections.singletonMap("error", error));
        String msg = error.getMessage();
        if (msg != null) {
            m.add(Collections.singletonMap("msg", msg));
        }
        return this;
    }

    String formattedNow() {
        DateTime now = DateTime.now().withZone(DateTimeZone.UTC);
        DateTimeFormatter fmt = ISODateTimeFormat.dateTime();
        return fmt.print(now);
    }

    static int pid = -1;
    int pid() {
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
        StringBuilder msg = new StringBuilder();
        List<Object> stuff = new LinkedList<>(m);
        MapBuilder mb = new MapBuilder();
        for (Iterator<Object> it = stuff.iterator(); it.hasNext();) {
            Object o = it.next();
            CharSequence s = null;
            if (o == null) {
                continue;
            } else if (o instanceof CharSequence) {
                s = (CharSequence) o;
                it.remove();
            } else if (o instanceof Boolean || o instanceof Number) {
                s = "" + o;
            } else if (o instanceof Map) {
                Map<?, ?> m = ((Map) o);
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    if ("msg".equals(e.getKey())) {
                        Object ob = e.getValue();
                        if (ob instanceof CharSequence) {
                            s = (CharSequence) ob;
                        } else {
                            mb.put("_msg", ob);
                        }
                    } else {
                        mb.put("" + e.getKey(), e.getValue());
                    }
                }

                it.remove();
            } else if (o instanceof List) {
                List<?> l = (List<?>) o;
                int sz = l.size();
                for (int i = 0; i < sz; i++) {
                    mb.put(i + "", l.get(i));
                }
            } else {
                try {
                    Map<?, ?> mm = config.mapper().readValue(config.mapper().writeValueAsBytes(o), Map.class);
                    for (Map.Entry<?, ?> e : mm.entrySet()) {
                        mb.put("" + e.getKey(), e.getValue());
                    }
                } catch (IOException ex) {
                    Logger.getLogger(LogImpl.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            if (o instanceof Map) {
                Map<?, ?> m = (Map) o;
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    if (!"msg".equals(e.getKey())) {
                        mb.put(e.getKey() + "", e.getValue());
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

        mb.put("name", name)
                .put("msg", msg)
                .put("v", 0)
                .put("time", formattedNow())
                .put("pid", pid())
                .put("level", level.ordinal())
                .put("hostname", config.hostname());
        sink.push(level, mb.build());
    }

    @Override
    public Log<T> addIfNotNull(String name, Object value) {
        if (value != null) {
            return add(name, value);
        }
        return this;
    }
}
