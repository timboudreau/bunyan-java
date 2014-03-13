package com.mastfrog.bunyan;

import com.mastfrog.bunyan.type.Debug;
import com.mastfrog.bunyan.type.Fatal;
import com.mastfrog.bunyan.type.Info;
import com.mastfrog.bunyan.type.LogLevel;
import com.mastfrog.bunyan.type.Trace;
import com.mastfrog.bunyan.type.Warn;

/**
 *
 * @author Tim Boudreau
 */
public class Logger {

    private final String name;
    private final LogSink sink;
    private final LoggingConfig config;
    private final Loggers loggers;

    Logger(String name, LogSink sink, LoggingConfig config, Loggers loggers) {
        this.name = name;
        this.sink = sink;
        this.config = config;
        this.loggers = loggers;
    }

    public final String name() {
        return name;
    }

    <T extends LogLevel<T>> Log<T> log(T level, Object... records) {
        LogImpl<T> result = new LogImpl<T>(name, level, sink, config);
        for (Object o : records) {
            result.add(o);
        }
        return result;
    }

    public Log<Trace> trace(Object... records) {
        return log(loggers.trace, records);
    }

    public Log<Debug> debug(Object... records) {
        return log(loggers.debug, records);
    }

    public Log<Info> info(Object... records) {
        return log(loggers.info, records);
    }

    public Log<Warn> warn(Object... records) {
        return log(loggers.warn, records);
    }

    public Log<com.mastfrog.bunyan.type.Error> error(Object... records) {
        return log(loggers.error, records);
    }

    public Log<Fatal> fatal(Object... records) {
        return log(loggers.fatal, records);
    }

    public Logger child(Object... stuff) {
        return new ChildLogger(name, sink, config, loggers, stuff);
    }

    private static final class ChildLogger extends Logger {

        public ChildLogger(String name, LogSink sink, LoggingConfig config, Loggers loggers, Object... stuff) {
            super(name, sink, config, loggers);
        }

        @Override
        <T extends LogLevel<T>> Log<T> log(T level, Object... records) {
            Log<T> result = super.log(level, records);
            for (Object o : records) {
                result.add(o);
            }
            return result;
        }

    }

    public String toString() {
        return name;
    }
}
