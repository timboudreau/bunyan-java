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
package com.mastfrog.bunyan.java.util.logging;

import com.google.inject.AbstractModule;
import static com.mastfrog.util.preconditions.Checks.notNull;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * Configures java.util.logging to use bunyan-java as a handler. If the <code>
 * autoConfigure</code> parameter to the constructor is set to true or the
 * no-arg constructor is used, this class will attempt to programmatically
 * remove all handlersfrom the root logger and add a bunyan handler. If
 * not, set your java.util.logging configuration to use
 * com.mastfrog.bunyan.java.util.logging.BunyanAppender, and <i>include this module in your
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
public final class BunyanJavaLoggingModule extends AbstractModule {

    private final boolean autoConfigure;
    private final Map<String, String> levels = new HashMap<>();

    public BunyanJavaLoggingModule(boolean autoConfigure) {
        this.autoConfigure = autoConfigure;
    }

    public BunyanJavaLoggingModule() {
        this(true);
    }

    @Override
    protected void configure() {
        bind(HandlerRegistry.class).asEagerSingleton();
        if (autoConfigure) {
            autoConfigure();
        }
    }

    public BunyanJavaLoggingModule setLevel(String pkg, Level level) {
        levels.put(notNull("pkg", pkg), notNull("level", level).getName());
        return this;
    }

    private void autoConfigure() {
        LogManager lm = LogManager.getLogManager();
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(BunyanHandler.class.getClassLoader());
            lm.readConfiguration(configuration(levels));
            // Force initialization here
            Logger.getGlobal().info("java.util.logging bunyan handler installed");
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            Thread.currentThread().setContextClassLoader(cl);
        }
    }

    private static InputStream configuration(Map<String, String> levels) {
        Properties props = new Properties();
        props.setProperty("handlers", BunyanHandler.class.getName());
        props.setProperty(".level", "INFO");
        for (Map.Entry<String, String> e : levels.entrySet()) {
            props.setProperty(e.getKey() + ".level", e.getValue());
        }
        props.setProperty(".useParentHandlers", "true");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        props.save(baos, "x");
        return new ByteArrayInputStream(baos.toByteArray());
    }

}
