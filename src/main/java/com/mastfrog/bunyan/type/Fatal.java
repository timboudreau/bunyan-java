package com.mastfrog.bunyan.type;

import com.mastfrog.bunyan.Loggers;
import com.mastfrog.bunyan.LoggingConfig;
import javax.inject.Inject;
import javax.inject.Provider;

/**
 *
 * @author Tim Boudreau
 */
public class Fatal extends AbstractLogLevel<Fatal> {

    @Inject
    Fatal(LoggingConfig config, Provider<Loggers> loggers) {
        super(60, config, loggers);
    }
}
