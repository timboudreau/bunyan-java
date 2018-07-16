/*
 * The MIT License
 *
 * Copyright 2017 tim.
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
package com.mastfrog.bunyan.parse;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.name.Named;
import com.mastfrog.util.preconditions.Exceptions;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.inject.Inject;

/**
 * Factory for Bunyan-format log streams over files, with options to map to
 * objects.
 *
 * @author Tim Boudreau
 */
public final class LogStreamFactory {

    private final Path path;
    private final ObjectMapper mapper;

    @Inject
    public LogStreamFactory(Path path, @Named("bunyan-java") ObjectMapper mapper) {
        this.path = path;
        this.mapper = mapper;
    }

    /**
     * Potential results from a function passed to one of the read() methods.
     */
    public enum ReadResult {
        CONTINUE, STOP
    }

    /**
     * Stream the log record to the passed reader function.
     *
     * @param reader A reader
     * @throws IOException if something goes wrong
     */
    public void read(Function<LogRecord, ReadResult> reader) throws IOException {
        read(null, reader);
    }

    /**
     * Get a stream of log records for reading.
     *
     * @return A stream
     * @throws IOException if something goes wrong
     */
    public Stream<LogRecord> stream() throws IOException {
        return stream(null);
    }

    /**
     * Get a stream, using the passed predicate for filtering.
     *
     * @see LogFilter
     * @param filter The filter, or null for none
     * @return A stream of log records
     * @throws IOException if something goes wrong
     */
    public Stream<LogRecord> stream(Predicate<LogRecord> filter) throws IOException {
        Stream<String> textLines = Files.lines(path, Charset.forName("UTF-8"));
        Predicate<String> prefilter = filter instanceof LogFilter ? ((LogFilter) filter).prefilterPredicate() : new JsonHashCheck();
        if (prefilter != null) {
            textLines = textLines.filter(prefilter);
        }
        Stream<LogRecord> lines = textLines.map((String t) -> {
            try {
                return mapper.readValue(t, LogRecord.class);
            } catch (IOException ex) {
                return Exceptions.chuck(ex);
            }
        });
        if (filter != null) {
            lines = lines.filter(filter);
        }
        return lines;
    }

    /**
     * Create a stream of some other object type which can be read as JSON from
     * the stream.
     *
     * @param <T> The type
     * @param pred An optional filter predicate
     * @param type The object type to create - must be compatible with some
     * subset of the tags in the lines of Bunyan-style logging that match the
     * filter predicate; need not have fields for all of them.
     * @return A stream of the passed type
     * @throws IOException if something goes wrong
     */
    public <T> Stream<T> convertedStream(Predicate<LogRecord> pred, Class<T> type) throws IOException {
        final ObjectMapper configuredMapper = mapper.copy()
                .configure(DeserializationFeature.ACCEPT_FLOAT_AS_INT, true)
                .configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false)
                .configure(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES, false)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false)
                .configure(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY, false)
                .configure(DeserializationFeature.FAIL_ON_UNRESOLVED_OBJECT_IDS, false)
                .configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true)
                .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
        return stream(pred).map((record) -> {
            try {
                byte[] b = configuredMapper.writeValueAsBytes(record.toMap());
                return mapper.readValue(b, type);
            } catch (IOException ex) {
                return Exceptions.chuck(ex);
            }
        });
    }

    /**
     * Read the records in the stream, using the passed filter, passing them to
     * the passed reader function, and then closing the stream.
     *
     * @param filter The filter predicate, may be null
     * @param reader A function that will consume lines
     * @throws IOException if something goes wrong
     */
    public void read(Predicate<LogRecord> filter, Function<LogRecord, ReadResult> reader) throws IOException {
        try (Stream<LogRecord> lines = stream(filter)) {
            lines.forEachOrdered((t) -> {
                ReadResult res = reader.apply(t);
                if (res != ReadResult.CONTINUE && res != null) {
                    throw new Abort();
                }
            });
        } catch (Abort abort) {
            //do nothing
        }
    }

    /**
     * Read the records in the stream, converting them to the passed type and
     * using the passed filter, and passing them to the reader function.
     *
     * @param <T> The type
     * @param filter The filter
     * @param type The object type to create - must be compatible with some
     * subset of the tags in the lines of Bunyan-style logging that match the
     * filter predicate; need not have fields for all of them.
     * @param reader
     * @throws IOException
     */
    public <T> void readConverted(Predicate<LogRecord> filter, Class<T> type, Function<T, ReadResult> reader) throws IOException {
        try (Stream<T> lines = convertedStream(filter, type)) {
            lines.forEachOrdered((t) -> {
                ReadResult res = reader.apply(t);
                if (res != ReadResult.CONTINUE && res != null) {
                    throw new Abort();
                }
            });
        } catch (Abort abort) {
            //do nothing
        }
    }

    private static final class Abort extends RuntimeException {

        @Override
        public synchronized Throwable fillInStackTrace() {
            // performance
            return this;
        }

        @Override
        public StackTraceElement[] getStackTrace() {
            // performance
            return new StackTraceElement[0];
        }
    }
}
