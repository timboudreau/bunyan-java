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

package com.mastfrog.bunyan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mastfrog.bunyan.parse.LogRecord;
import com.mastfrog.bunyan.parse.LogStreamFactory;
import com.mastfrog.bunyan.type.Fatal;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.jackson.DurationSerializationMode;
import com.mastfrog.jackson.JacksonModule;
import com.mastfrog.jackson.TimeSerializationMode;
import com.mastfrog.settings.Settings;
import com.mastfrog.settings.SettingsBuilder;
import java.io.File;
import java.io.IOException;
import org.junit.After;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author Tim Boudreau
 */
public class NioLoggingTest {

    Dependencies deps;
    Loggers loggers;
    File logfile;
    ObjectMapper mapper;

    @Before
    public void bef() throws IOException {
        File tmp = new File(System.getProperty("java.io.tmpdir"));
        logfile = new File(tmp, getClass().getSimpleName() + "-" + Long.toString(System.currentTimeMillis(), 36));

        Settings settings = new SettingsBuilder()
                .add(LoggingModule.SETTINGS_KEY_ASYNC_LOGGING, false)
                .add(LoggingModule.SETTINGS_KEY_LOG_TO_CONSOLE, false)
                .add(LoggingModule.SETTINGS_KEY_LOG_FILE, logfile.getAbsolutePath())
                .build();

        deps = new Dependencies(settings, new JacksonModule().withJavaTimeSerializationMode(TimeSerializationMode.TIME_AS_ISO_STRING, DurationSerializationMode.DURATION_AS_MILLIS), new LoggingModule(false)
                .bindLogger("foo"));

        loggers = deps.getInstance(Loggers.class);
        mapper = deps.getInstance(ObjectMapper.class);

        LogWriter w = deps.getInstance(LogWriter.class);
        assertTrue(w instanceof DefaultLogWriter);
        assertTrue(((DefaultLogWriter)w).delegate instanceof NioFileWriter);
    }

    @After
    public void after() throws IOException {
        if (logfile != null && logfile.exists()) {
            assertTrue(logfile.delete());
        }
    }

    @Test
    public void test() throws IOException {
        assertNotNull(loggers);
        for (int i = 0; i < 50; i++) {
            try (Log<Fatal> l = loggers.logger("foo").fatal("hey")) {
                l.add("data", "Item " + i).add("index", i);
            }
        }
        // Force log flush and thread pool close
        deps.shutdown();
        assertTrue(logfile.exists());
        assertNotEquals(0L, logfile.length());
        int[] ix = new int[1];
        LogStreamFactory f = new LogStreamFactory(logfile.toPath(), mapper);
        f.read((LogRecord t) -> {
            String s = t.toString();
            int index = ix[0]++;
            assertNotNull("Data element missing in " + s, t.get("data"));
            assertEquals("Wrong data element in " + s, "Item " + index, t.get("data"));
            assertEquals("Wrong index element in " + s, Integer.valueOf(index), t.get("index"));
            return LogStreamFactory.ReadResult.CONTINUE;
        });
        assertEquals(50, ix[0]);
    }
}
