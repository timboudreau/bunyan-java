/*
 * The MIT License
 *
 * Copyright 2015 tim.
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

import static com.mastfrog.bunyan.LoggingModule.SETTINGS_KEY_LOG_FILE;
import com.mastfrog.bunyan.type.Fatal;
import com.mastfrog.giulius.ShutdownHookRegistry;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.TestWith;
import com.mastfrog.jackson.JacksonModule;
import com.mastfrog.util.collections.MapBuilder;
import java.io.File;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Named;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author Tim Boudreau
 */
@RunWith(GuiceRunner.class)
@TestWith({JacksonModule.class, BenchmarkTest.LM.class})
public class BenchmarkTest {

    private static final long WARMUP_MS = 5000;
    private static final long REAL_MS = 15000;

    private static final Map<String, Object> JUNK = new MapBuilder()
            .put("skiddoo", 23).put("foogle", "bumblewhatzit")
            .put("widgets", new MapBuilder().put("sub1", "woo").put("sub2", "hoo").build())
            .put("universe", 42.0001D).put("hey", true).build();

    static class LM extends LoggingModule {

        LM() {
            super.bindLogger("whoozie");
        }
    }

    @Test
    public void test(final Loggers loggers, final @Named("whoozie") Logger logger, Cleanup flean) throws InterruptedException {
        // For testing before/after optimizations
//        if (true) {
//            return;
//        }
        System.out.println("LOGGERS: " + loggers);

        try (Log<Fatal> log = logger.log(loggers.fatal)) {
            System.out.println("LOG: " + log);
            log.message("start-warmup");
        }
        final long start = System.currentTimeMillis();
        long fin;
        int i;
        for (i = 0;; i++) {
            try (Log<Fatal> log = logger.log(loggers.fatal)) {
                log.message("item");
                log.add("iter", i);
                log.add(JUNK);
            }
            if ((fin = System.currentTimeMillis() - start) >= WARMUP_MS) {
                break;
            }
        }
        System.out.println("Wrote " + i + " records in " + fin + " ms");
        try (Log<Fatal> log = logger.log(loggers.fatal)) {
            log.message("finish-warmup");
        }

        try (Log<Fatal> log = logger.log(loggers.fatal)) {
            log.message("start-real");
        }
        final long start2 = System.currentTimeMillis();
        long fin2;
        int i2;
        for (i2 = 0;; i2++) {
            try (Log<Fatal> log = logger.log(loggers.fatal)) {
                log.message("item");
                log.add("iter", i2);
                log.add(JUNK);
            }
            if ((fin2 = System.currentTimeMillis() - start2) >= REAL_MS) {
                break;
            }
        }
        System.out.println("Wrote " + i2 + " records in " + fin2 + " ms");
        try (Log<Fatal> log = logger.log(loggers.fatal)) {
            log.message("finish-real");
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
