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
package com.mastfrog.bunyan.log4j;

import com.mastfrog.bunyan.Log;
import com.mastfrog.bunyan.Loggers;
import com.mastfrog.bunyan.type.LogLevel;
import com.mastfrog.util.thread.AtomicLinkedQueue;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.ThrowableInformation;

/**
 *
 * @author Tim Boudreau
 */
public final class BunyanAppender extends AppenderSkeleton {

    private final AtomicReference<Consumer<LoggingEvent>> sink = new AtomicReference<>(new Queuer());

    @SuppressWarnings("LeakingThisInConstructor")
    public BunyanAppender() {
        AppenderRegistry.register(this);
    }

    void setLoggers(Loggers loggers) {
        Consumer<LoggingEvent> nue = loggers == null ? new Queuer() : new Sender(loggers);
        Consumer<LoggingEvent> old = sink.getAndSet(nue);
        if (old instanceof Queuer) {
            Queuer q = (Queuer) old;
            for (LoggingEvent evt : q.drain()) {
                nue.accept(evt);
            }
        }
    }

    @Override
    protected void append(LoggingEvent event) {
        sink.get().accept(event);
    }

    @Override
    public void close() {
        sink.getAndSet(new NoOp());
    }

    @Override
    public boolean requiresLayout() {
        return false;
    }

    private final class Queuer implements Consumer<LoggingEvent> {

        private final AtomicLinkedQueue<LoggingEvent> cache = new AtomicLinkedQueue<>();

        @Override
        public void accept(LoggingEvent t) {
            cache.add(t);
        }

        List<LoggingEvent> drain() {
            return cache.drain();
        }
    }

    private final class Sender implements Consumer<LoggingEvent> {

        private final Loggers loggers;

        public Sender(Loggers loggers) {
            this.loggers = loggers;
        }

        @Override
        public void accept(LoggingEvent t) {
            LogLevel<?> level = levelFor(t.getLevel());
            try (Log<?> log = level.log(t.getLoggerName())) {
                log.add(t.getMessage());
                Map<?, ?> m = t.getProperties();
                if (m != null && !m.isEmpty()) {
                    log.add("props", m);
                }
                log.add("thread", t.getThreadName());
                ThrowableInformation info = t.getThrowableInformation();
                if (info != null && info.getThrowable() != null) {
                    log.add(info.getThrowable());
                    log.add("stack", info.getThrowableStrRep());
                }
            }
        }

        private final LogLevel<?> levelFor(Level level) {
            switch (level.toInt()) {
                case Level.DEBUG_INT:
                    return loggers.debug;
                case Level.ERROR_INT:
                    return loggers.error;
                case Level.FATAL_INT:
                    return loggers.fatal;
                case Level.INFO_INT:
                    return loggers.info;
                case Level.WARN_INT:
                    return loggers.warn;
                case Level.TRACE_INT:
                    return loggers.trace;
                case Level.OFF_INT:
                    return loggers.trace;
                case Level.ALL_INT:
                    return loggers.fatal;
                default:
                    return loggers.info;
            }
        }
    }

    private static final class NoOp implements Consumer<LoggingEvent> {

        @Override
        public void accept(LoggingEvent t) {
            // do nothing
        }
    }
}
