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

    private final LogWriter delegate;

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
    public void write(String s) {
        delegate.write(s);
    }

    @Override
    public String toString() {
        return super.toString() + "{delegate=" + delegate + "}";
    }
}
