package com.mastfrog.bunyan;

import static com.mastfrog.bunyan.LoggingModule.SETTINGS_KEY_ASYNC_LOGGING;
import static com.mastfrog.bunyan.LoggingModule.SETTINGS_KEY_LOG_TO_CONSOLE;
import static com.mastfrog.bunyan.LoggingModule.SETTINGS_KEY_LOG_FILE;
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
            File f = new File(file);
            w = SimpleLogWriter.forFile(f);
            boolean consoleToo = settings.getBoolean(SETTINGS_KEY_LOG_TO_CONSOLE, false);
            if (consoleToo) {
                w = SimpleLogWriter.combine(w, new SimpleLogWriter());
            }
        } else {
            w = new SimpleLogWriter();
        }
        if (settings.getBoolean(SETTINGS_KEY_ASYNC_LOGGING, false)) {
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
}
