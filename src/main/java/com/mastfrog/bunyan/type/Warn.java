package com.mastfrog.bunyan.type;

import com.mastfrog.bunyan.Loggers;
import com.mastfrog.bunyan.LoggingConfig;
import javax.inject.Inject;
import javax.inject.Provider;

/**
 *
 * @author Tim Boudreau
 */
public class Warn extends AbstractLogLevel<Warn> {

    @Inject
    Warn(LoggingConfig config, Provider<Loggers> loggers) {
        super(40, config, loggers);
    }
}
