package com.mastfrog.bunyan;

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
        String file = settings.getString("log.file");
        LogWriter w;
        if (file != null) {
            File f = new File(file);
            w = SimpleLogWriter.forFile(f);
            boolean consoleToo = settings.getBoolean("log.console", false);
            if (consoleToo) {
                w = SimpleLogWriter.combine(w, new SimpleLogWriter());
            }
        } else {
            w = new SimpleLogWriter();
        }
        if (settings.getBoolean("log.async", false)) {
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
