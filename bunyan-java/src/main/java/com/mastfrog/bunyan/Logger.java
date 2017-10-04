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

import com.mastfrog.bunyan.type.Debug;
import com.mastfrog.bunyan.type.Fatal;
import com.mastfrog.bunyan.type.Info;
import com.mastfrog.bunyan.type.LogLevel;
import com.mastfrog.bunyan.type.Trace;
import com.mastfrog.bunyan.type.Warn;

/**
 * A factory for log records
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

    /**
     * The name of this logger
     *
     * @return The name
     */
    public final String name() {
        return name;
    }

    /**
     * Create a new log record
     *
     * @param <T> The log level
     * @param level The log level
     * @param records Initial log records
     * @return A log record
     */
    <T extends LogLevel<T>> Log<T> log(T level, Object... records) {
        if (!level.isEnabled()) {
            return new NullLogger<>(level);
        }
        LogImpl<T> result = new LogImpl<>(name, level, sink, config);
        for (Object o : records) {
            result.add(o);
        }
        return result;
    }

    /**
     * Create a trace-level log record
     *
     * @param records Records to include (can also be added using Log.add())
     * @return A log record
     */
    public Log<Trace> trace(Object... records) {
        return log(loggers.trace, records);
    }

    /**
     * Create a debug-level log record
     *
     * @param records Records to include (can also be added using Log.add())
     * @return A log record
     */
    public Log<Debug> debug(Object... records) {
        return log(loggers.debug, records);
    }

    /**
     * Create an info-level log record
     *
     * @param records Records to include (can also be added using Log.add())
     * @return A log record
     */
    public Log<Info> info(Object... records) {
        return log(loggers.info, records);
    }

    /**
     * Create a warning-level log record
     *
     * @param records Records to include (can also be added using Log.add())
     * @return A log record
     */
    public Log<Warn> warn(Object... records) {
        return log(loggers.warn, records);
    }

    /**
     * Create an error-level log record
     *
     * @param records Records to include (can also be added using Log.add())
     * @return A log record
     */
    public Log<com.mastfrog.bunyan.type.Error> error(Object... records) {
        return log(loggers.error, records);
    }

    /**
     * Create a fatal-level log record
     *
     * @param records Records to include (can also be added using Log.add())
     * @return A log record
     */
    public Log<Fatal> fatal(Object... records) {
        return log(loggers.fatal, records);
    }

    /**
     * Create a child logger which includes the passed records in all log
     * records it creates
     *
     * @param records Records to include in all log records
     * @return A logger
     */
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
