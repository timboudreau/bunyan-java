package com.mastfrog.bunyan;

import com.google.inject.AbstractModule;
import com.google.inject.Provider;
import com.google.inject.name.Names;
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
    public static final String DEFAULT_LOG_FILE = "stats.log";
    public static final String SETTINGS_KEY_ASYNC_LOGGING = "log.async";
    public static final String SETTINGS_KEY_LOG_TO_CONSOLE = "log.console";

    public LoggingModule bindLogger(String name) {
        loggers.add(name);
        return this;
    }

    @Override
    protected void configure() {
        for (String s : loggers) {
            bind(Logger.class).annotatedWith(Names.named(s))
                    .toProvider(new LoggerProvider(s, binder().getProvider(Loggers.class)));
        }
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
