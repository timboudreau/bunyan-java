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
package com.mastfrog.bunyan.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mastfrog.bunyan.LogSink;
import com.mastfrog.bunyan.recovery.RecoverySink;
import com.mastfrog.bunyan.type.LogLevel;
import com.mastfrog.util.Exceptions;
import com.mastfrog.util.collections.CollectionUtils;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;
import static java.util.concurrent.TimeUnit.SECONDS;
import java.util.function.Predicate;
import org.junit.After;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author Tim Boudreau
 */
public class FailoverSinkTest {

    File dir;
    TestSink sink;
    ObjectMapper mapper = new ObjectMapper();
    boolean noCleanup = true;

    Predicate<Exception> allExceptions = (e) -> {
        return true;
    };

    @Before
    public void before() {
        File tmp = new File(System.getProperty("java.io.tmpdir"));
        dir = new File(tmp, FailoverSinkTest.class.getSimpleName() + "-" + Long.toString(System.currentTimeMillis(), 36));
        sink = new TestSink();
    }

    @Test
    public void testExceptionThrownInFailoverDoesNotLoseMessages() throws Exception {

        sink.on(2, () -> {
            throw new RuntimeException();
        });

        // LogSink real, File recoveryDir, ObjectMapper mapper, Predicate<Throwable> failureDetector
        RecoverySink test = new RecoverySink(sink, dir, mapper, allExceptions);
        assertFalse(test.isActiveMode());
        List<Map<String, Object>> l = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Map<String, Object> m = map(i, "foo-" + i);
            l.add(m);
            if (i == 3 || i == 6) {
                test.setActiveMode(true);
            }
            test.push(null, m);
        }
        assertTrue(test.isActiveMode());
        for (int i = 0; i < l.size(); i++) {
            sink.assertHas(l.get(i));
            sink.assertHas(i, l.get(i));
        }
    }

    @Test(timeout = 30000)
    public void testConcurrent() throws Exception {
        RecoverySink f = new RecoverySink(sink, dir, mapper, allExceptions);
        int threads = 7;
        int count = 1000;
        f.setActiveMode(true);
        for (int i = 0; i < count * threads; i += 10) {
            int ii = i;
            sink.on(i + 5, () -> {
                System.out.println("THROW IT AT " + (ii + 5));
                throw new Exception(Thread.currentThread() + "");
            });
            sink.on(i + 9, () -> {
                System.out.println("BACK TO ACTIVE AT " + (ii + 9));
                f.setActiveMode(true);
                return null;
            });
        }

        Set<Map<String, Object>> all = new LinkedHashSet<>();
        Phaser b = new Phaser(threads);
        ExecutorService svc = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            int ii = i;
            final String nm = "th-" + i;
            svc.submit(() -> {
                b.arriveAndAwaitAdvance();
                try {
                    for (int j = 0; j < count; j++) {
                        Map<String, Object> mm = map(j, nm, "item-" + j);
                        all.add(mm);
                        try {
                            f.push(null, mm);
                        } catch (Exception ex) {
                            System.out.println(ex);
                        }
                        if ((ii % 37) == 0) {
                            f.setActiveMode(false);
                        } else if ((ii % 53) == 0) {
                            f.setActiveMode(true);
                        }
                    }
                } finally {
                    f.setActiveMode(true);
                    latch.countDown();
                }
                return null;
            });
        }
        b.arriveAndDeregister();
        latch.await(20, SECONDS);
        f.setActiveMode(true);
        sink.assertHasAll(all);
    }

    private Map<String, Object> map(int ix, String... props) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ix", ix);
        for (String p : props) {
            m.put(p, true);
        }
        return m;
    }

    static final class TestSink implements LogSink {

        private final List<Map<String, Object>> all = new CopyOnWriteArrayList<>();
        private int index;
        private final Map<Integer, Callable<Void>> m = new HashMap<>();

        void assertHasAll(Collection<Map<String, Object>> m) {
            boolean result = all.containsAll(m);
            if (!result) {
                Set<Map<String, Object>> s = CollectionUtils.disjunction(all, m);
                List<Map<String, Object>> l = new ArrayList<>(s);
                l.sort((a, b) -> {
                    Integer aval = ((Integer) a.get("ix"));
                    Integer bval = ((Integer) b.get("ix"));
                    return aval.compareTo(bval);
                });
                fail("Missing " + l);
            }
        }

        void assertHas(Map<String, Object> m) {
            assertTrue("Missing " + m, all.contains(m));
        }

        void assertHas(int ix, Map<String, Object> m) {
            assertTrue("Missing " + m + " at " + ix + " - instead have " + all.get(ix), m.equals(all.get(ix)));
        }

        void on(int index, Callable<Void> doit) {
            m.put(index, doit);
        }

        @Override
        public void push(LogLevel level, Map<String, Object> logrecord) {
            int ix = index++;
            Callable<Void> c = m.get(ix);
            if (c != null) {
                System.out.println("Invoke a callable at " + ix);
                try {
                    c.call();
                } catch (Exception e) {
                    System.out.println("  re-chucking  for " + logrecord);
                    e.printStackTrace();
                    Exceptions.chuck(e);
                }
            }
            assertFalse("Received record twice: " + logrecord, all.contains(logrecord));
            System.out.println("\nRECEIVE " + logrecord + " at " + all.size() + "\n");
            all.add(logrecord);
        }
    }

    @After
    public void cleanup() throws IOException {
        if (noCleanup) {
            return;
        }
        String currentTest = System.getProperty("testMethodQname");
        if (currentTest != null) {
            String s = System.getProperty(currentTest + ".failed", "false");
            if (!"false".equals(s)) {
                System.out.println("Not deleting " + dir + " because " + currentTest + " failed");
                return;
            }
        }
        List<Path> toDelete = new ArrayList<>();
        try {
            Files.walk(dir.toPath(), 100, FileVisitOption.FOLLOW_LINKS)
                    .forEach((Path p) -> {
                        toDelete.add(p);
                    });
        } catch (NoSuchFileException ex) {
            // ok
        }
        Collections.sort(toDelete,
                (a, b) -> {
                    int ad = a.getNameCount();
                    int bd = b.getNameCount();
                    return ad == bd ? 0 : ad > bd ? -1 : 1;
                });
        for (Path p : toDelete) {
            try {
                Files.delete(p);
            } catch (NoSuchFileException ex) {
                // ok
            }
        }
    }
}
