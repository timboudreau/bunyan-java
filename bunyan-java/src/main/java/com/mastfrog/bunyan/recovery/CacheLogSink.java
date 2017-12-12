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

import com.mastfrog.bunyan.BatchSink;
import com.mastfrog.bunyan.LogSink;
import com.mastfrog.bunyan.type.LogLevel;
import com.mastfrog.util.thread.AtomicLinkedQueue;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Tim Boudreau
 */
final class CacheLogSink implements LogSink {

    private final AtomicLinkedQueue<Map<String, Object>> records = new AtomicLinkedQueue<>();

    @Override
    public void push(LogLevel level, Map<String, Object> logrecord) {
        System.out.println(" --write to temp cache " + logrecord);
        records.add(logrecord);
    }

    List<Map<String, Object>> drain() {
        return records.drain();
    }

    void drainTo(LogSink dest) {
        if (dest == this) {
            return;
        }
        if (dest instanceof BatchSink) {
            ((BatchSink) dest).pushMany(records.drain());
        } else {
            records.drain((Map<String, Object> m) -> {
                dest.push(null, m);
            });
        }
    }

    @Override
    public String toString() {
        return "CacheLogSink<" + records.size() + ">";
    }
}
