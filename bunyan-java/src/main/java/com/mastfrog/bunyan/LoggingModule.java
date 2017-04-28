package com.mastfrog.bunyan;

import com.google.inject.AbstractModule;
import com.google.inject.Provider;
import com.google.inject.name.Names;
import com.mastfrog.jackson.JacksonConfigurer;
import com.mastfrog.jackson.JacksonModule;
import java.util.LinkedList;
import java.util.List;

/**
 *
 * @author Tim Boudreau
 */
public class LoggingModule extends AbstractModule {

    private final List<String> loggers = new LinkedList<>();
    public static final String SETTINGS_KEY_LOG_FILE = "log.file";
    public static final String SETTINGS_KEY_LOG_LEVEL = "log.level";
    public static final String SETTINGS_KEY_LOG_HOSTNAME = "log.hostname";
    public static final String SETTINGS_KEY_ASYNC_LOGGING = "log.async";
    public static final String SETTINGS_KEY_LOG_TO_CONSOLE = "log.console";
    public static final String SETTINGS_KEY_LOG_FILE_GZIPPED = "log.gzip";

    public static final String GUICE_BINDING_OBJECT_MAPPER = "bunyan-java";
    private final JacksonModule jacksonModule;

    public LoggingModule() {
        this(true);
    }

    public LoggingModule(boolean useMetaInfServicesJacksonConfigurers) {
        jacksonModule = new JacksonModule(GUICE_BINDING_OBJECT_MAPPER, useMetaInfServicesJacksonConfigurers)
                .withConfigurer(new JacksonConfig());
    }

    public LoggingModule bindLogger(String name) {
        if (!loggers.contains(name)) {
            loggers.add(name);
        }
        return this;
    }

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
