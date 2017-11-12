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
package com.mastfrog.bunyan.log4j2.appender;

import com.mastfrog.bunyan.Log;
import com.mastfrog.bunyan.Loggers;
import com.mastfrog.bunyan.type.LogLevel;
import com.mastfrog.util.thread.AtomicLinkedQueue;
import java.io.Serializable;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.filter.LevelRangeFilter;
import org.apache.logging.log4j.core.impl.MutableLogEvent;
import org.apache.logging.log4j.core.layout.JsonLayout;

/**
 *
 * @author Tim Boudreau
 */
@Plugin(name = "bunyan", category = "Bunyan", elementType = Appender.ELEMENT_TYPE, printObject = true)
public class BunyanAppender extends AbstractAppender {

    @SuppressWarnings("LeakingThisInConstructor")
    public BunyanAppender(String name, Filter filter, Layout<? extends Serializable> layout) {
        super(name, filter, layout);
        AppenderRegistry.register(this);
    }

    @SuppressWarnings("LeakingThisInConstructor")
    public BunyanAppender(String name, Filter filter, Layout<? extends Serializable> layout, boolean ignoreExceptions) {
        super(name, filter, layout, ignoreExceptions);
        AppenderRegistry.register(this);
    }

    public BunyanAppender() {
        this("bunyan", LevelRangeFilter.createFilter(Level.INFO, Level.FATAL, Filter.Result.ACCEPT, Filter.Result.NEUTRAL), JsonLayout.createDefaultLayout(), true);
    }

    private final AtomicReference<Consumer<LogEvent>> sink = new AtomicReference<>(new Queuer());

    void setLoggers(Loggers loggers) {
        Consumer<LogEvent> nue = loggers == null ? new Queuer() : new Sender(loggers);
        Consumer<LogEvent> old = sink.getAndSet(nue);
        if (old instanceof Queuer) {
            Queuer q = (Queuer) old;
            for (LogEvent evt : q.drain()) {
                nue.accept(evt);
            }
        }
    }

    @Override
    public void append(LogEvent event) {
        sink.get().accept(event);
    }

    private final class Queuer implements Consumer<LogEvent> {

        private final AtomicLinkedQueue<LogEvent> cache = new AtomicLinkedQueue<>();

        @Override
        public void accept(LogEvent t) {
            cache.add(t);
        }

        List<LogEvent> drain() {
            return cache.drain();
        }
    }

    private final class Sender implements Consumer<LogEvent> {

        private final Loggers loggers;

        public Sender(Loggers loggers) {
            this.loggers = loggers;
        }

        @Override
        public void accept(LogEvent t) {
            LogLevel<?> level = levelFor(t.getLevel());
            try (Log<?> log = level.log(t.getLoggerName())) {
                log.add(t.getMessage().getFormattedMessage());
                log.add("thread", t.getThreadName());
                MutableLogEvent mle;
                if (t.getThrown() != null) {
                    log.add("error", t.getThrown());
                    log.add("stack", t.getThrown().getStackTrace());
                }
            }
        }

        private final LogLevel<?> levelFor(Level level) {
            if (Level.DEBUG.equals(level)) {
                return loggers.debug;
            } else if (Level.ERROR.equals(level)) {
                return loggers.error;
            } else if (Level.FATAL.equals(level)) {
                return loggers.fatal;
            } else if (Level.INFO.equals(level)) {
                return loggers.info;
            } else if (Level.WARN.equals(level)) {
                return loggers.warn;
            } else if (Level.TRACE.equals(level)) {
                return loggers.trace;
            } else if (Level.ALL.equals(level)) {
                return loggers.info;
            } else {
                return loggers.info;
            }
        }
    }

    private static final class NoOp implements Consumer<LogEvent> {

        @Override
        public void accept(LogEvent t) {
            // do nothing
        }
    }

}
