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
package com.mastfrog.acteur.bunyan;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.RequestLogger;
import com.mastfrog.acteur.util.ErrorInterceptor;
import com.mastfrog.acteur.util.RequestID;
import com.mastfrog.bunyan.Log;
import com.mastfrog.bunyan.Logger;
import com.mastfrog.bunyan.LoggingConfig;
import com.mastfrog.bunyan.LoggingModule;
import com.mastfrog.jackson.JacksonConfigurer;
import io.netty.handler.codec.http.HttpResponseStatus;

/**
 *
 * @author Tim Boudreau
 */
public class ActeurBunyanModule extends AbstractModule {

    private final LoggingModule loggingModule;
    public static final String ERROR_LOGGER = "error";
    public static final String ACCESS_LOGGER = "requests";
    private String requestLoggerLevel = "debug";

    public ActeurBunyanModule() {
        this(true);
    }

    public ActeurBunyanModule(boolean useMetaInfServicesJacksonConfigurers) {
        loggingModule = new LoggingModule(useMetaInfServicesJacksonConfigurers).withConfigurer(new BunyanJacksonConfig())
                .bindLogger(ERROR_LOGGER).bindLogger(ACCESS_LOGGER);
    }

    public ActeurBunyanModule bindLogger(String name) {
        checkLaunched();
        loggingModule.bindLogger(name);
        return this;
    }

    public ActeurBunyanModule withConfigurer(JacksonConfigurer configurer) {
        checkLaunched();
        loggingModule.withConfigurer(configurer);
        return this;
    }

    public ActeurBunyanModule setRequestLoggerLevel(String level) {
        LoggingConfig.throwIfInvalidLevelName(level);
        checkLaunched();
        this.requestLoggerLevel = level;
        return this;
    }

    void checkLaunched() {
        if (launched) {
            throw new IllegalStateException("Cannot configure after the injector has been created");
        }
    }

    private boolean launched;
    private static final String GUICE_BINDING_REQUEST_LOGGER_LEVEL = "_requestLoggerLevel";

    @Override
    protected void configure() {
        launched = true;
        install(loggingModule);
        bind(ErrorInterceptor.class).to(ErrorH.class);
        bind(RequestLogger.class).to(JsonRequestLogger.class);
        bind(String.class).annotatedWith(Names.named(GUICE_BINDING_REQUEST_LOGGER_LEVEL))
                .toInstance(this.requestLoggerLevel);
    }

    @Singleton
    static class ErrorH implements ErrorInterceptor {

        private final Logger logger;

        @Inject
        ErrorH(@Named(ERROR_LOGGER) Logger logger) {
            this.logger = logger;
        }

        Throwable last;

        @Override
        public void onError(Throwable err) {
            if (last == err) { // FIXME in acteur
                return;
            }
            last = err;
            if (err instanceof Error) {
                logger.fatal(err.getClass().getName()).add(err).close();
            } else {
                logger.error(err.getClass().getName()).add(err).close();
            }
        }
    }

    @Singleton
    public static class JsonRequestLogger implements RequestLogger {

        private final Logger logger;
        private final String level;

        @Inject
        JsonRequestLogger(@Named(ACCESS_LOGGER) Logger logger, @Named(GUICE_BINDING_REQUEST_LOGGER_LEVEL) String level) {
            this.logger = logger;
            this.level = level;
        }

        @Override
        public void onBeforeEvent(RequestID rid, Event<?> event) {
        }

        @Override
        public void onRespond(RequestID rid, Event<?> event, HttpResponseStatus status) {
            Log<?> log;
            switch (level) {
                case LoggingConfig.LEVEL_DEBUG:
                    log = logger.debug("request", event);
                    break;
                case LoggingConfig.LEVEL_ERROR:
                    log = logger.error("request", event);
                    break;
                case LoggingConfig.LEVEL_FATAL:
                    log = logger.fatal("request", event);
                    break;
                case LoggingConfig.LEVEL_INFO:
                    log = logger.info("request", event);
                    break;
                case LoggingConfig.LEVEL_WARNING:
                    log = logger.warn("request", event);
                    break;
                case LoggingConfig.LEVEL_TRACE:
                    log = logger.trace("request");
                    break;
                default:
                    throw new AssertionError(level);
            }
            log.add("dur", rid.getDuration().getMillis()).add("status", status.code()).close();
        }
    }

}
