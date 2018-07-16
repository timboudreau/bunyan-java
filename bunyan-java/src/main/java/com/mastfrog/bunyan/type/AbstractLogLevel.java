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
package com.mastfrog.bunyan.type;

import com.mastfrog.bunyan.Log;
import com.mastfrog.bunyan.Loggers;
import com.mastfrog.bunyan.LoggingConfig;
import com.mastfrog.util.preconditions.Checks;
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
        Checks.notNull("name", name);
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
