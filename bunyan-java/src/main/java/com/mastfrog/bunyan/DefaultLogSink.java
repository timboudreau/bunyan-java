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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.name.Named;
import com.mastfrog.bunyan.type.LogLevel;
import java.util.Map;
import java.util.logging.Level;
import javax.inject.Inject;

/**
 *
 * @author Tim Boudreau
 */
final class DefaultLogSink implements LogSink {

    private final ObjectMapper mapper;
    private final LogWriter writer;

    @Inject
    DefaultLogSink(@Named(LoggingModule.GUICE_BINDING_OBJECT_MAPPER) ObjectMapper mapper, LogWriter writer, LoggingConfig config) {
        this.mapper = mapper;
        this.writer = writer instanceof DefaultLogWriter ? ((DefaultLogWriter) writer).delegate : writer;
    }

    public void push(LogLevel level, Map<String, Object> log) {
        try {
            if (writer instanceof LogWriter.Bytes) {
                ((LogWriter.Bytes) writer).write(mapper.writeValueAsBytes(log));
            } else {
                String s = mapper.writeValueAsString(log);
                writer.write(s);
            }
        } catch (JsonProcessingException ex) {
            // Give up
            java.util.logging.Logger.getLogger(LogSink.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    void rawWrite(CharSequence s) {
        writer.write(s);
    }

    @Override
    public String toString() {
        return super.toString() + "{writer=" + writer + "}";
    }
}
