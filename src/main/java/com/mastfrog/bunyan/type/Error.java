package com.mastfrog.bunyan.type;

import com.mastfrog.bunyan.Loggers;
import com.mastfrog.bunyan.LoggingConfig;
import javax.inject.Inject;
import javax.inject.Provider;

/**
 *
 * @author Tim Boudreau
 */
public class Error extends AbstractLogLevel<Error> {

    @Inject
    Error(LoggingConfig config, Provider<Loggers> loggers) {
        super(50, config, loggers);
    }
}
