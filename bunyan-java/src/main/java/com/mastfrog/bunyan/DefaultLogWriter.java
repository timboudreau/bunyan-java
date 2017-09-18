/*
 * The MIT License
 *
 * Copyright 2015 Tim Boudreau.
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
package com.mastfrog.bunyan;

import static com.mastfrog.bunyan.LoggingModule.SETTINGS_KEY_ASYNC_LOGGING;
import static com.mastfrog.bunyan.LoggingModule.SETTINGS_KEY_LOG_TO_CONSOLE;
import static com.mastfrog.bunyan.LoggingModule.SETTINGS_KEY_LOG_FILE;
import static com.mastfrog.bunyan.LoggingModule.SETTINGS_KEY_LOG_FILE_GZIPPED;
import static com.mastfrog.bunyan.LoggingModule.SETTINGS_KEY_STREAM_BUFFER_SIZE;
import com.mastfrog.giulius.ShutdownHookRegistry;
import com.mastfrog.settings.Settings;
import java.io.File;
import java.io.IOException;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 *
 * @author Tim Boudreau
 */
@Singleton
final class DefaultLogWriter implements LogWriter {

    final LogWriter delegate;

    @Inject
    DefaultLogWriter(Settings settings, ShutdownHookRegistry reg) throws IOException {
        String file = settings.getString(SETTINGS_KEY_LOG_FILE);
        LogWriter w;
        if (file != null) {
            boolean gzip = settings.getBoolean(SETTINGS_KEY_LOG_FILE_GZIPPED, false);
            int bufferSize = settings.getInt(SETTINGS_KEY_STREAM_BUFFER_SIZE, -1);
            File f = new File(file);
            w = SimpleLogWriter.forFile(f, gzip, bufferSize);
            boolean consoleToo = settings.getBoolean(SETTINGS_KEY_LOG_TO_CONSOLE, false);
            if (consoleToo) {
                System.err.println("Creating a console logger");
                w = SimpleLogWriter.combine(w, new SimpleLogWriter());
            }
        } else {
            System.err.println("No log file " + SETTINGS_KEY_LOG_FILE + " - write to console only");
            w = new SimpleLogWriter();
        }
        if (settings.getBoolean(SETTINGS_KEY_ASYNC_LOGGING, true)) {
            w = SimpleLogWriter.async(w);
        }
        delegate = w;
        if (w instanceof SimpleLogWriter) {
            ((SimpleLogWriter) w).hook(reg);
        }
    }

    @Override
    public void write(CharSequence s) {
        delegate.write(s);
    }

    @Override
    public String toString() {
        return super.toString() + "{delegate=" + delegate + "}";
    }
}
