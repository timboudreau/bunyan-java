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

import com.mastfrog.bunyan.LoggerTest.LM;
import static com.mastfrog.bunyan.LoggingModule.SETTINGS_KEY_LOG_FILE;
import com.mastfrog.bunyan.type.Warn;
import com.mastfrog.giulius.ShutdownHookRegistry;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.TestWith;
import com.mastfrog.jackson.JacksonModule;
import com.mastfrog.util.collections.MapBuilder;
import java.io.File;
import java.util.Map;
import java.util.Random;
import javax.inject.Inject;
import javax.inject.Named;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author Tim Boudreau
 */
@RunWith(GuiceRunner.class)
@TestWith({JacksonModule.class, LM.class})
public class LoggerTest {

    @Test
    public void test(Loggers loggers, Thing thing, Cleanup clean) throws InterruptedException {
        try (Log<Warn> log = thing.logger.warn("wunk").message("hoobie")) {
            log.add("foo", "bar");
            log.add("baz", 23);
            Bean b = new Bean();
            b.hoogie = "wuz";
            b.isTrue = true;
            log.add("bean", b);
        }
        Random r = new Random(System.currentTimeMillis());
        for (int i = 0; i < 150; i++) {
            try (Log<?> log = loggers.log(loggers.error, "foodbar").message("whatzit")) {
                log.add("ran", r.nextInt());
                log.add("ix", i);
            };
        }
        thing.logger.debug(new IllegalStateException("Hoobie")).message("A bad thing happened").close();
    }

    private static class Thing {

        private final Logger logger;

        @Inject
        public Thing(@Named("whoozie") Logger logger) {
            this.logger = logger;
        }
    }

    public static final class Bean {

        public String hoogie;
        public boolean isTrue;
        public Map<String, Object> nested = new MapBuilder().put("hey", "you").build();
    }

    static class LM extends LoggingModule {

        LM() {
            super.bindLogger("whoozie");
        }
    }

    static final class Cleanup implements Runnable {

        private final String logfile;

        @Inject
        Cleanup(@Named(SETTINGS_KEY_LOG_FILE) String logfile, ShutdownHookRegistry reg) {
            this.logfile = logfile;
            reg.add(this);
        }

        @Override
        public void run() {
            boolean result = new File(logfile).delete();
            System.err.println("Deleted " + logfile + "? " + result);
        }
    }
}
