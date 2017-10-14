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

import com.google.inject.AbstractModule;
import com.mastfrog.bunyan.ChildLoggerTest.MM;
import com.mastfrog.bunyan.type.Info;
import com.mastfrog.bunyan.type.LogLevel;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.TestWith;
import static com.mastfrog.util.collections.CollectionUtils.map;
import java.util.Map;
import javax.inject.Named;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author Tim Boudreau
 */
@RunWith(GuiceRunner.class)
@TestWith(MM.class)
public class ChildLoggerTest {

    @Test
    public void testChildLogger(@Named("x") Logger logger, SinkImpl sink) {
        try (Log<Info> l = logger.info("hey").add("foo", "bar")) {
            l.add("skiddoo", 23);
        }
        Map<String, Object> m = sink.assertLogged("skiddoo", 23);
        assertEqualsS("name", "x", m);
        assertEqualsS("msg", "hey", m);

        Logger child = logger.child("woo", map("yoo").to("hoo").build());

        try (Log<Info> l = child.info("hey").add("foo", "bar")) {
            l.add("skiddoo", 24);
        }
        m = sink.assertLogged("skiddoo", 24);
        assertEqualsS("name", "x", m);
        assertEqualsS("msg", "woo hey", m);
        assertEqualsS("yoo", "hoo", m);
    }

    static void assertEqualsS(String key, Object value, Map<String, Object> m) {
        Object o = m.get(key);
        assertNotNull("Does not contain " + key + ": " + m, o);
        if (o instanceof CharSequence) {
            o = o.toString();
        }
        assertEquals(value, o);
    }

    static final class MM extends AbstractModule {

        @Override
        protected void configure() {
            install(new LoggingModule().bindLogger("x"));
            SinkImpl sink = new SinkImpl();
            bind(LogSink.class).toInstance(sink);
            bind(SinkImpl.class).toInstance(sink);
        }
    }

    static final class SinkImpl implements LogSink {

        private Map<String, Object> last;
        private LogLevel lastLevel;

        Map<String, Object> assertLogged(String key, Object val) {
            Map<String, Object> m = assertLogged();
            assertTrue(m.containsKey(key));
            assertEqualsS(key, val, m);
            return m;
        }

        Map<String, Object> assertLogged() {
            Map<String, Object> l = last;
            last = null;
            assertNotNull(l);
            return l;
        }

        @Override
        public void push(LogLevel level, Map<String, Object> logrecord) {
            last = logrecord;
            lastLevel = level;
        }

    }
}
