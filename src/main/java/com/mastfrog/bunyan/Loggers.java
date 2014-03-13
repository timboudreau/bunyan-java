package com.mastfrog.bunyan;

import com.mastfrog.bunyan.type.Debug;
import com.mastfrog.bunyan.type.Fatal;
import com.mastfrog.bunyan.type.Info;
import com.mastfrog.bunyan.type.LogLevel;
import com.mastfrog.bunyan.type.Trace;
import com.mastfrog.bunyan.type.Warn;
import javax.inject.Inject;

/**
 *
 * @author Tim Boudreau
 */
public final class Loggers {

    public final Trace trace;
    public final Debug debug;
    public final Info info;
    public final Warn warn;
    public final com.mastfrog.bunyan.type.Error error;
    public final Fatal fatal;
    private final LogSink sink;
    private final LoggingConfig config;

    @Inject
    Loggers(Trace trace, Debug debug, Info info, Warn warn, com.mastfrog.bunyan.type.Error error, Fatal fatal,
            LogSink sink, LoggingConfig config) {
        this.trace = trace;
        this.debug = debug;
        this.info = info;
        this.warn = warn;
        this.error = error;
        this.fatal = fatal;
        this.sink = sink;
        this.config = config;
    }

    public Logger logger(String name) {
        return new Logger(name, sink, config, this);
    }

    public <T extends LogLevel> Log<T> log(T level, String name) {
        return logger(name).log(level);
    }
}
