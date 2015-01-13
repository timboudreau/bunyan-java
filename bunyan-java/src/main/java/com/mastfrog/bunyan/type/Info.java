package com.mastfrog.bunyan.type;

import com.mastfrog.bunyan.Loggers;
import com.mastfrog.bunyan.LoggingConfig;
import javax.inject.Inject;
import javax.inject.Provider;

/**
 *
 * @author Tim Boudreau
 */
public class Info extends AbstractLogLevel<Info> {

    @Inject
    Info(LoggingConfig config, Provider<Loggers> loggers) {
        super(30, config, loggers);
    }
}
