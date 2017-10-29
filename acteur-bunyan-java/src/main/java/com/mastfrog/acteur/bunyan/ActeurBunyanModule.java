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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.RequestLogger;
import com.mastfrog.acteur.debug.HttpProbe;
import com.mastfrog.acteur.debug.Probe;
import com.mastfrog.acteur.util.ErrorInterceptor;
import com.mastfrog.acteur.util.RequestID;
import com.mastfrog.acteurbase.ActeurState;
import com.mastfrog.bunyan.Log;
import com.mastfrog.bunyan.Logger;
import com.mastfrog.bunyan.LoggingConfig;
import com.mastfrog.bunyan.LoggingModule;
import com.mastfrog.jackson.JacksonConfigurer;
import com.mastfrog.util.Exceptions;
import static com.mastfrog.util.collections.CollectionUtils.map;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 *
 * @author Tim Boudreau
 */
public class ActeurBunyanModule extends AbstractModule {

    private final LoggingModule loggingModule;
    public static final String ERROR_LOGGER = "error";
    public static final String ACCESS_LOGGER = "requests";
    private String requestLoggerLevel = "debug";
    private boolean bindErrorInterceptor = true;
    private boolean useProbe = Boolean.getBoolean("acteur.debug");

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

    public ActeurBunyanModule dontBindErrorInterceptor() {
        bindErrorInterceptor = false;
        return this;
    }

    private boolean includeBody;

    public ActeurBunyanModule useProbe(boolean includeBody) {
        this.useProbe = true;
        this.includeBody = includeBody;
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
        if (useProbe) {
            bindLogger("probe");
        }
        launched = true;
        install(loggingModule);
        if (bindErrorInterceptor && !Boolean.getBoolean("unit.test")) {
            bind(ErrorInterceptor.class).to(ErrorH.class);
        }
        bind(RequestLogger.class).to(JsonRequestLogger.class);
        bind(String.class).annotatedWith(Names.named(GUICE_BINDING_REQUEST_LOGGER_LEVEL))
                .toInstance(this.requestLoggerLevel);

        if (useProbe) {
            bind(Probe.class).toProvider(ProbeLogger.class);
            bind(Boolean.class).annotatedWith(Names.named("_probeBody")).toInstance(includeBody);
        }
    }

    static class ProbeLogger extends HttpProbe {

        private final Logger logger;
        private final Cache<RequestID, Logger> kids = CacheBuilder.newBuilder().weakKeys().build();
        private final boolean bodies;

        @Inject
        ProbeLogger(@Named("probe") Logger logger, @Named("_probeBody") boolean bodies) {
            this.logger = logger;
            this.bodies = bodies;
        }

        private Logger childLogger(RequestID id, HttpEvent evt) {
            try {
                Logger l = kids.get(id, () -> {
                    return logger.child(
                            map("rid").to(id.stringValue())
                                    .map("path").to(evt.path())
                                    .map("early").to(evt.isPreContent())
                                    .map("method").to(evt.method().name())
                                    .build());
                });
                return l;
            } catch (ExecutionException ex) {
                Exceptions.printStackTrace(ex);
                return logger;
            }
        }

        @Override
        protected void onInfo(String info, Object... objs) {
            logger.info(String.format(info, objs)).close();
        }

        @Override
        protected void onBeforeSendResponse(RequestID id, HttpEvent httpEvent, Acteur acteur, HttpResponseStatus status, boolean hasListener, Object message) {
            childLogger(id, httpEvent).trace("onBeforeSendResponse").add("acteur", acteur.getClass().getName())
                    .add("status", status).add("listener", hasListener).add("message", bodies ? message : "-").close();
        }

        @Override
        protected void onFallthrough(RequestID id, HttpEvent evt) {
            childLogger(id, evt).trace("onFallthrough");
        }

        @Override
        protected void onActeurWasRun(RequestID id, HttpEvent evt, Page page, Acteur acteur, ActeurState state) {
            Map<String, Object> stateInfo = state == null ? null : map("finished").to(state.isFinished())
                    .map("type").to(state.getClass().getName()).build();
            childLogger(id, evt).trace("onActeurWasRun").add("page", page.getClass().getName())
                    .add("acteur", acteur.getClass().getName()).add("state", stateInfo).close();
        }

        @Override
        protected void onBeforeRunPage(RequestID id, HttpEvent evt, Page page) {
            childLogger(id, evt).trace("onBeforeRunPage").add("page", page.getClass().getSimpleName()).close();
        }

        @Override
        protected void onBeforeProcessRequest(RequestID id, HttpEvent req) {
            childLogger(id, req).trace("onBeforeProcessRequest");
        }
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
        private final RequestLogRecordDecorator decorator;

        @Inject
        JsonRequestLogger(@Named(ACCESS_LOGGER) Logger logger, @Named(GUICE_BINDING_REQUEST_LOGGER_LEVEL) String level,
                RequestLogRecordDecorator decorator) {
            this.logger = logger;
            this.level = level.intern();
            this.decorator = decorator;
        }

        @Override
        public void onBeforeEvent(RequestID rid, Event<?> event) {
            // do nothing
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
            decorator.decorate(log, event, status, rid);
            log.add("id", rid.stringValue())
                    .add("dur", rid.getDuration().toMillis())
                    .add("status", status.code())
                    .close();
        }
    }

}
