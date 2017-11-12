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

import com.mastfrog.bunyan.Loggers;
import com.mastfrog.giulius.ShutdownHookRegistry;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 *
 * @author Tim Boudreau
 */
@Singleton
final class AppenderRegistry {

    private static final List<Reference<BunyanAppender>> APPENDER_REFS = new ArrayList<>(3);
    private static AtomicReference<WeakReference<Loggers>> LOGGERS_REF = new AtomicReference<>();

    @Inject
    AppenderRegistry(Loggers loggers, ShutdownHookRegistry registry) {
        LOGGERS_REF.set(new WeakReference<>(loggers));
        for (Reference<BunyanAppender> r : APPENDER_REFS) {
            BunyanAppender b = r.get();
            if (b != null) {
                b.setLoggers(loggers);
            }
        }
        registry.add(() -> {
            unregister();
            return null;
        });
    }

    static Loggers getLoggers() {
        WeakReference<Loggers> lg = LOGGERS_REF.get();
        if (lg != null) {
            return lg.get();
        }
        return null;
    }

    static void register(BunyanAppender appender) {
        APPENDER_REFS.add(new WeakReference<BunyanAppender>(appender));
        Loggers loggers = getLoggers();
        if (loggers != null) {
            appender.setLoggers(loggers);
        }
    }

    static void unregister() {
        for (Reference<BunyanAppender> r : APPENDER_REFS) {
            BunyanAppender b = r.get();
            if (b != null) {
                b.setLoggers(null);
            }
        }
    }
}
