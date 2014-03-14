package com.mastfrog.bunyan;

import com.mastfrog.bunyan.type.Debug;
import com.mastfrog.bunyan.type.Fatal;
import com.mastfrog.bunyan.type.Info;
import com.mastfrog.bunyan.type.LogLevel;
import com.mastfrog.bunyan.type.Trace;
import com.mastfrog.bunyan.type.Warn;
import javax.inject.Inject;

/**
 * Factory for loggers and log levels.  This object can be injected to get
 * loggers, <i>or</i> you can use <a href="LoggingModule.html">LoggingModule</a>
 * and simply inject <code>&#064;Named("loggername") Logger logger)</code>.
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

    /**
     * Get a logger
     * @param name
     * @return 
     */
    public Logger logger(String name) {
        return new Logger(name, sink, config, this);
    }

    /**
     * Create a new log record with the specified level and name
     * @param <T> The log level tyep
     * @param level The log level
     * @param name The name of the logger
     * @return A log record
     */
    public <T extends LogLevel> Log<T> log(T level, String name) {
        return logger(name).log(level);
    }
}
