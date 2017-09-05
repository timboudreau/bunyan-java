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

import com.google.inject.AbstractModule;
import com.google.inject.Provider;
import com.google.inject.name.Names;
import com.mastfrog.jackson.JacksonConfigurer;
import com.mastfrog.jackson.JacksonModule;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

/**
 *
 * @author Tim Boudreau
 */
public class LoggingModule extends AbstractModule {

    private final List<String> loggers = new LinkedList<>();
    /**
     * Settings key for the file to write to, if any.
     */
    public static final String SETTINGS_KEY_LOG_FILE = "log.file";
    /**
     * Settings key for the minimum log level.
     */
    public static final String SETTINGS_KEY_LOG_LEVEL = "log.level";
    /**
     * Settings key for the host name used in log records (if not set,
     * will be gotten from the system).
     */
    public static final String SETTINGS_KEY_LOG_HOSTNAME = "log.hostname";
    /**
     * Settings key - if true, log records are buffered and written by a background thread;
     * more performant but may result in data loss on crash.
     */
    public static final String SETTINGS_KEY_ASYNC_LOGGING = "log.async";
    /**
     * Settings key for whether logging should also be written to the system
     * out.
     */
    public static final String SETTINGS_KEY_LOG_TO_CONSOLE = "log.console";
    /**
     * Settings key for whether log files should be gzip-compressed.
     */
    public static final String SETTINGS_KEY_LOG_FILE_GZIPPED = "log.gzip";
    /**
     * Settings key for the buffer size for output (and gzip) streams that
     * are written to when logging.  A value &lt:1 uses the default of 
     * 1024;  a value of 1 means no buffering.
     */
    public static final String SETTINGS_KEY_STREAM_BUFFER_SIZE = "log.buffer";

    /**
     * Name used by the Named annotation to identify the ObjectMapper that
     * will be injected into loggers.  If unusual objects are to be serialized
     * into log records, it will need to be configured to handle them.
     */
    public static final String GUICE_BINDING_OBJECT_MAPPER = "bunyan-java";
    private final JacksonModule jacksonModule;

    public static final DateTimeFormatter ISO_INSTANT;

    static {
        ISO_INSTANT = new DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .appendInstant()
                .toFormatter(Locale.US);
    }

    public LoggingModule() {
        this(true);
    }

    /**
     * Create a new logging modules.
     * 
     * @param useMetaInfServicesJacksonConfigurers If true, use the Java 
     * Extension Mechanism to look up JacksonConfigurers on the classpath,
     * which will be used to configure the ObjectMapper used to render log
     * records as JSON.
     */
    public LoggingModule(boolean useMetaInfServicesJacksonConfigurers) {
        jacksonModule = new JacksonModule(GUICE_BINDING_OBJECT_MAPPER, useMetaInfServicesJacksonConfigurers)
                .withConfigurer(new JacksonConfig());
    }

    /**
     * Add the name of a logger that should be bound, which can be
     * injected using the Named annotation.
     * 
     * @param name The name of the logger
     * @return This
     */
    public LoggingModule bindLogger(String name) {
        if (!loggers.contains(name)) {
            loggers.add(name);
        }
        return this;
    }

    /**
     * Include a particular JacksonConfigurer to configure the 
     * ObjectMapper used to serialize log lines.  Note that you will
     * write broken logs if you use any pretty-printing options that split
     * lines here - this is why we have a separate Guice binding for this
     * mapper.
     * 
     * @param configurer The configurer.
     * @return This
     */
    public LoggingModule withConfigurer(JacksonConfigurer configurer) {
        jacksonModule.withConfigurer(configurer);
        return this;
    }

    @Override
    protected void configure() {
        for (String s : loggers) {
            bind(Logger.class).annotatedWith(Names.named(s))
                    .toProvider(new LoggerProvider(s, binder().getProvider(Loggers.class)));
        }
        install(jacksonModule);
    }

    private static class LoggerProvider implements Provider<Logger> {

        private final String name;
        private final Provider<Loggers> loggers;

        public LoggerProvider(String name, Provider<Loggers> loggers) {
            this.name = name;
            this.loggers = loggers;
        }

        @Override
        public Logger get() {
            return loggers.get().logger(name);
        }
    }
}
