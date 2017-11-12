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
package com.mastfrog.bunyan.log4j;

import com.google.inject.AbstractModule;
import com.mastfrog.bunyan.LogSink;
import com.mastfrog.bunyan.LoggingModule;
import com.mastfrog.bunyan.log4j.BunyanAppenderTest.CustomSinkModule;
import com.mastfrog.bunyan.type.LogLevel;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.TestWith;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author Tim Boudreau
 */
@RunWith(GuiceRunner.class)
@TestWith({BunyanLog4JModule.class, CustomSinkModule.class, LoggingModule.class})
public class BunyanAppenderTest {

    static Logger LOGGER = Logger.getLogger(BunyanAppenderTest.class);

    @Test
    public void testSomeMethod(CSink sink) {
        Exception ex = new IllegalStateException();
        LOGGER.log(Level.WARN, "This is a warning");
        LOGGER.log(Level.INFO, "This is info");
        LOGGER.log(Level.FATAL, "Something thrown", ex);

        sink.assertHasMessage("warn", "This is a warning");
        sink.assertHasMessage("info", "This is info");
        sink.assertHasMessage("fatal", "Something thrown");
    }

    static final class CustomSinkModule extends AbstractModule {

        @Override
        protected void configure() {
            CSink sink = new CSink();
            bind(LogSink.class).toInstance(sink);
            bind(CSink.class).toInstance(sink);
        }
    }

    private static final class CSink implements LogSink {

        private final List<Entry> entries = new LinkedList<>();

        Entry assertHasMessage(String level, String msg) {
            boolean found = false;
            Entry result = null;
            for (Entry e : entries) {
                if (e.level.name().equalsIgnoreCase(level)) {
                    Object o = e.record.get("msg");
                    if (o != null) {
                        if (msg.equals(o.toString())) {
                            found = true;
                            result = e;
                            break;
                        }
                    }
                }
            }
            assertTrue("Did not find '" + msg + "' at '" + level + "' in " + entries, found);
            return result;
        }

        @Override
        public void push(LogLevel level, Map<String, Object> logrecord) {
            Entry e = new Entry(level, logrecord);
            entries.add(e);
            System.out.println(e);
        }

    }

    static final class Entry {

        private final LogLevel level;
        private final Map<String, Object> record;

        public Entry(LogLevel level, Map<String, Object> record) {
            this.level = level;
            this.record = record;
        }

        public String toString() {
            return level + ": " + record;
        }
    }
}
