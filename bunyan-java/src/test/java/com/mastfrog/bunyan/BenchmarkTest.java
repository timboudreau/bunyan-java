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
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.giulius.ShutdownHookRegistry;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.TestWith;
import com.mastfrog.jackson.JacksonModule;
import com.mastfrog.util.collections.MapBuilder;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Named;
import org.junit.After;
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
            super.bindLogger("whoozie").bindLogger("whatzis");
        }
    }

    private static final boolean RUN_AT_ALL = false;

    @Test
    public void test(final Loggers loggers, final @Named("whoozie") Logger logger, Cleanup flean) throws InterruptedException {
        // For testing before/after optimizations
        try (Log<Fatal> log = logger.log(loggers.fatal)) {
            System.out.println("LOG: " + log);
            log.message("start-warmup");
        }
        if (!RUN_AT_ALL) {
            return;
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

    @Test
    public void testMultithreaded(final Loggers loggers, final @Named("whatzis") Logger logger, Cleanup flean, Dependencies deps) throws InterruptedException {
        if (!RUN_AT_ALL) {
            return;
        }
        // warm up
        System.out.println("Run multithreaded");
        int ct = runMultithreaded(5, 5, loggers, logger);
        System.out.println("Warmup ran " + ct + " in 5 seconds");

        long start = System.currentTimeMillis();
        int real = runMultithreaded(15, 5, loggers, logger);
        long elapsed = System.currentTimeMillis() - start;
        System.out.println("Real ran " + real + " in 15 seconds (actual " + elapsed + "ms)");
        deps.shutdown();
    }

    private final ExecutorService exe = Executors.newCachedThreadPool();

    int runMultithreaded(int seconds, int count, Loggers loggers, Logger logger) throws InterruptedException {
        List<MultiWriter> l = new ArrayList<>();
        CountDownLatch onExit = new CountDownLatch(count);
        CountDownLatch startLatch = new CountDownLatch(1);
        for (int i = 0; i < count; i++) {
            MultiWriter m = new MultiWriter(loggers, logger, onExit, startLatch);
            exe.submit(m);
            l.add(m);
        }
        Thread.sleep(50);
        startLatch.countDown();
        Thread.sleep(1000 * seconds);
        for (MultiWriter m : l) {
            m.done = true;
        }
        onExit.await(5, TimeUnit.SECONDS);
        int total = 0;
        for (MultiWriter m : l) {
            total += m.i;
        }
        return total;
    }

    @After
    public void shutdown() {
        exe.shutdownNow();
    }

    static final class MultiWriter implements Callable<Void> {

        private final Loggers loggers;

        volatile boolean done;

        int i;
        private final Logger logger;
        private final CountDownLatch onExit;
        private final CountDownLatch startLatch;

        public MultiWriter(Loggers loggers, Logger logger, CountDownLatch onExit, CountDownLatch startLatch) {
            this.loggers = loggers;
            this.logger = logger;
            this.onExit = onExit;
            this.startLatch = startLatch;
        }

        @Override
        public Void call() throws Exception {
            startLatch.await();
            try {
                for (i = 0;; i++) {
                    try (Log<Fatal> log = logger.log(loggers.fatal)) {
                        log.message("item");
                        log.add("iter", i);
                        log.add(JUNK);
                    }
                    if (done) {
                        break;
                    }
                }
            } finally {
                onExit.countDown();
            }
            return null;
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
