package com.mastfrog.bunyan.type;

import com.mastfrog.bunyan.Log;
import com.mastfrog.bunyan.Loggers;
import com.mastfrog.bunyan.LoggingConfig;
import javax.inject.Provider;

/**
 *
 * @author Tim Boudreau
 */
abstract class AbstractLogLevel<T extends LogLevel> implements LogLevel<T> {

    private final String name;
    private final int ordinal;
    private final LoggingConfig config;
    private final Provider<Loggers> loggers;

    AbstractLogLevel(int ordinal, LoggingConfig config, Provider<Loggers> loggers) {
        name = getClass().getSimpleName().toLowerCase();
        this.ordinal = ordinal;
        this.config = config;
        this.loggers = loggers;
    }

    @SuppressWarnings("unchecked")
    public Log<T> log(String name) {
        return loggers.get().<T>log((T) this, name);
    }

    public boolean isEnabled() {
        return ordinal() >= config.minimimLoggableLevel();
    }

    public final int ordinal() {
        return ordinal;
    }

    public final String name() {
        return name;
    }

    public final String toString() {
        return name;
    }

    public final boolean equals(Object o) {
        return o != null && o.getClass() == getClass();
    }

    public final int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public final int compareTo(LogLevel o) {
        Integer mine = ordinal();
        Integer theirs = o.ordinal();
        return mine.compareTo(theirs);
    }
}
