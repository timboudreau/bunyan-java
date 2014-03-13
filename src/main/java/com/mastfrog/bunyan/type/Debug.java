package com.mastfrog.bunyan.type;

import com.mastfrog.bunyan.Loggers;
import com.mastfrog.bunyan.LoggingConfig;
import javax.inject.Inject;
import javax.inject.Provider;

/**
 *
 * @author Tim Boudreau
 */
public final class Debug extends AbstractLogLevel<Debug> {

    @Inject
    Debug(LoggingConfig config, Provider<Loggers> loggers) {
        super(20, config, loggers);
    }
}
