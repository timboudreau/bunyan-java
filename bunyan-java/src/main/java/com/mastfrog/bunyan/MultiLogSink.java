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
package com.mastfrog.bunyan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.name.Named;
import com.mastfrog.bunyan.type.LogLevel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;

/**
 * Multiplexing LogSink - use for cases where you have a consumer of log records
 * that wants the raw map (say, storing log records in MongoDB). To use, bind
 * LogSink to MultiLogSink, and then implement any number of MultiLogSink.Sink
 * instances and bind them as eager singletons.
 *
 * @author Tim Boudreau
 */
public final class MultiLogSink implements LogSink {

    private final List<Sink> sinks = new ArrayList<>();
    private final ObjectMapper mapper;

    @Inject
    @SuppressWarnings("ResultOfObjectAllocationIgnored")
    MultiLogSink(DefaultLogSink defaultSink, @Named(LoggingModule.GUICE_BINDING_OBJECT_MAPPER) ObjectMapper mapper) {
        new DefaultWrapper(this, defaultSink);
        this.mapper = mapper;
    }

    @Override
    public void push(LogLevel level, Map<String, Object> logrecord) {
        for (Sink sink : sinks) {
            sink.push(level, logrecord, mapper);
        }
    }

    void register(Sink sink) {
        sinks.add(sink);
    }

    public static abstract class Sink {

        @SuppressWarnings("LeakingThisInConstructor")
        protected Sink(MultiLogSink multi) {
            multi.register(this);
        }

        /**
         * Receive a log record
         *
         * @param level The log level
         * @param logrecord The map
         * @param mapper The objectMapper registered for logging
         */
        public abstract void push(LogLevel level, Map<String, Object> logrecord, ObjectMapper mapper);
    }

    private static final class DefaultWrapper extends Sink {

        private final DefaultLogSink sink;

        public DefaultWrapper(MultiLogSink multi, DefaultLogSink sink) {
            super(multi);
            this.sink = sink;
        }

        @Override
        public void push(LogLevel level, Map<String, Object> logrecord, ObjectMapper mapper) {
            sink.push(level, logrecord);
        }
    }
}
