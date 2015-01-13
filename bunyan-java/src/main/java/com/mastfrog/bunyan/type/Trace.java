package com.mastfrog.bunyan.type;

import com.mastfrog.bunyan.Loggers;
import com.mastfrog.bunyan.LoggingConfig;
import javax.inject.Inject;
import javax.inject.Provider;

/**
 *
 * @author Tim Boudreau
 */
public class Trace extends AbstractLogLevel<Trace> {

    @Inject
    Trace(LoggingConfig config, Provider<Loggers> loggers) {
        super(10, config, loggers);
    }
}
