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
package com.mastfrog.bunyan.recovery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mastfrog.bunyan.LogSink;
import com.mastfrog.bunyan.type.LogLevel;
import com.mastfrog.util.Exceptions;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/**
 *
 * @author Tim Boudreau
 */
public class RecoverySink implements LogSink {

    private final AtomicReference<LogSink> writeTo = new AtomicReference<>();
    private final LogSink real;
    private final RecoveryStorage stor;
    private final AtomicBoolean active = new AtomicBoolean(false);
    private final Predicate<Exception> failureDetector;

    public RecoverySink(LogSink real, File recoveryDir, ObjectMapper mapper, Predicate<Exception> failureDetector) throws IOException {
        this.real = real;
        if (!Files.exists(recoveryDir.toPath())) {
            Files.createDirectories(recoveryDir.toPath());
        }
        stor = new RecoveryStorage(new RecoveryFiles(recoveryDir), mapper);
        writeTo.set(stor);
        this.failureDetector = failureDetector;
    }

    public boolean isActiveMode() {
        return active.get();
    }

    @SuppressWarnings("unchecked")
    public synchronized void setActiveMode(boolean val) throws Exception {
        System.out.println("SET ACTIVE MODE " + val);
//        try (NonThrowingAutoCloseable t = funLock.withWriteLock()) {
        if (active.compareAndSet(!val, val)) {
            CacheLogSink temp = new CacheLogSink();
            writeTo.set(temp);
            try {
                if (val) {
                    Map<?,?>[] last = new Map<?,?>[1];
                    boolean fullyRecovered = stor.recover((m) -> {
                        last[0] = m;
                        System.out.println("  RECOVER " + m);
                        real.push(null, m);
                    }, failureDetector);
                    if (fullyRecovered) {

                        System.out.println("FULLY RECOVERED");
                        writeTo.set(real);
                    } else {
                        System.out.println("NOT FULLY RECOVERED, BACK TO PASSIVE MODE");
                        active.set(false);
                        writeTo.set(stor);
                        if (last[0] != null) {
//                            System.out.println("PUSH LAST TO STOR: " + last[0]);
//                            stor.push(null, (Map<String, Object>) last[0]);
                        }
                    }
                } else {
                    writeTo.set(stor);
                }
            } finally {
                List<Map<String,Object>> l = temp.drain();
                for (Iterator<Map<String,Object>> it = l.iterator(); it.hasNext();) {
                    Map<String,Object> m = it.next();
                    try {
                        writeTo.get().push(null, m);
                    } catch (Exception ex) {
                        if (failureDetector.test(ex)) {
                            active.set(false);
                            writeTo.set(stor);
                            stor.push(null, m);
                        } else {
                            Exceptions.chuck(ex);
                        }
                    }
                }
//                temp.drainTo(writeTo.get());
            }
        }
//        }
    }

    @Override
    public synchronized void push(LogLevel level, Map<String, Object> logrecord) {
        if (active.get()) {
            try {
                writeTo.get().push(level, logrecord);
            } catch (Exception ex) {
                if (failureDetector.test(ex)) {
                    try {
                        setActiveMode(false);
                        writeTo.get().push(level, logrecord);
                    } catch (Exception ex1) {
                        ex.addSuppressed(ex1);
                        ex.printStackTrace();
                    }
                }
            }
        } else {
            System.out.println("Write to " + writeTo);
            writeTo.get().push(level, logrecord);
        }
    }
}
