/*
 * The MIT License
 *
 * Copyright 2017 Tim Boudreau.
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
package com.mastfrog.bunyan.log4j2.appender;

import com.google.inject.AbstractModule;
import org.apache.logging.log4j.core.config.ConfigurationFactory;

/**
 * Configures log4j to use bunyan-java as an appender. If the <code>
 * autoConfigure</code> parameter to the constructor is set to true or the
 * no-arg constructor is used, this class will attempt to programmatically
 * remove all appenders from the root log4j logger and add a bunyan appender. If
 * not, set your log4j configuration to use
 * com.mastfrog.bunyan.log4jj.BunyanAppender, and <i>include this module in your
 * Guice configuration</i> (failure to do so will result in all log records
 * being cached in memory waiting for bunyan-java to arrive on the scene, until
 * there is no more memory!).
 * <p>
 * On shutdown, as defined by Giulius's <code>ShutdownHookRegistry</code>
 * loggers are replaced with a no-op logger. If they persist (as in a unit test)
 * and a new BunyanLog4JModule is installed over a new injector, they will use
 * that one once reinitialized, so static logger references that survive will
 * use the logging mechanism of the current guice context (when/if there is one
 * again). This is generally useful only in unit tests, or applications which
 * reconfigure and recreate their configuration without JVM shutdown.
 *
 * @author Tim Boudreau
 */
public final class BunyanLog4JModule extends AbstractModule {

    private final boolean autoConfigure;

    public BunyanLog4JModule(boolean autoConfigure) {
        this.autoConfigure = autoConfigure;
    }

    public BunyanLog4JModule() {
        this(true);
    }

    @Override
    protected void configure() {
        bind(AppenderRegistry.class).asEagerSingleton();
        if (autoConfigure) {
            autoConfigure();
        }
    }

    private void autoConfigure() {
        ConfigurationFactory.setConfigurationFactory(new BunyanConfigurationFactory());
    }
}
