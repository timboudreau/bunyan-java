/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mastfrog.bunyan.java.util.logging;

import com.mastfrog.bunyan.Log;
import com.mastfrog.bunyan.Loggers;
import com.mastfrog.bunyan.type.LogLevel;
import com.mastfrog.util.thread.AtomicLinkedQueue;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 *
 * @author Tim Boudreau
 */
public class BunyanHandler extends Handler {

    private final AtomicReference<Consumer<LogRecord>> sink = new AtomicReference<>(new Queuer());

    @SuppressWarnings("LeakingThisInConstructor")
    public BunyanHandler() {
        HandlerRegistry.register(this);
    }

    void setLoggers(Loggers loggers) {
        Consumer<LogRecord> nue = loggers == null ? new Queuer() : new Sender(loggers);
        Consumer<LogRecord> old = sink.getAndSet(nue);
        if (old instanceof Queuer) {
            Queuer q = (Queuer) old;
            for (LogRecord evt : q.drain()) {
                nue.accept(evt);
            }
        }
    }

    @Override
    public void publish(LogRecord record) {
        sink.get().accept(record);
    }

    @Override
    public void flush() {
        // do nothing
    }

    @Override
    public void close() {
        sink.getAndSet(new NoOp());
    }

    private final class Queuer implements Consumer<LogRecord> {

        private final AtomicLinkedQueue<LogRecord> cache = new AtomicLinkedQueue<>();

        @Override
        public void accept(LogRecord t) {
            cache.add(t);
        }

        List<LogRecord> drain() {
            return cache.drain();
        }
    }

    private final class Sender implements Consumer<LogRecord> {

        private final Loggers loggers;

        public Sender(Loggers loggers) {
            this.loggers = loggers;
        }

        @Override
        public void accept(LogRecord t) {
            LogLevel<?> level = levelFor(t.getLevel());
            try (Log<?> log = level.log(t.getLoggerName())) {
                log.add(t.getMessage());
                Object[] params = t.getParameters();
                if (params != null && params.length > 0) {
                    log.add("params", params);
                }
                if (t.getThrown() != null) {
                    log.add(t.getThrown());
                }
                log.add("thread", t.getThreadID());
            }
        }

        LogLevel<?> levelFor(Level level) {
            switch (level.intValue()) {
                case -2147483648: // ALL
                    return loggers.fatal;
                case 700: // CONFIG
                    return loggers.info;
                case 500: // FINE
                case 400: // FINER
                    return loggers.debug;
                case 300: // FINEST
                case 2147483647: // OFF
                    return loggers.trace;
                case 800: // INFO
                    return loggers.info;
                case 1000: // SEVERE
                    return loggers.fatal;
                case 900: // WARNING
                    return loggers.warn;
            }
            return loggers.info;
        }
    }

    private static final class NoOp implements Consumer<LogRecord> {

        @Override
        public void accept(LogRecord t) {
            // do nothing
        }
    }

}
