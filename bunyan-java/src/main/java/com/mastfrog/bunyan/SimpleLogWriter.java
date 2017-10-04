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

import com.mastfrog.giulius.ShutdownHookRegistry;
import com.mastfrog.util.thread.AtomicLinkedQueue;
import com.mastfrog.util.thread.OneThreadLatch;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

/**
 *
 * @author Tim Boudreau
 */
public class SimpleLogWriter implements LogWriter {

    public void write(CharSequence s) {
        System.out.println(s);
    }

    public static LogWriter combine(LogWriter... writers) {
        return new Combined(writers);
    }

    public static LogWriter forFile(File f, boolean gzip, int bufferSize) throws IOException {
        return !gzip ? new NioFileWriter(f, false, bufferSize) : new FileWriter(f, gzip, bufferSize);
    }

    public static LogWriter async(LogWriter writer) {
        return writer instanceof AsyncLogWriter ? (LogWriter) writer : new AsyncLogWriter(writer);
    }

    public String toString() {
        return "Console log writer";
    }

    void hook(ShutdownHookRegistry reg) {

    }

    public final SimpleLogWriter async() {
        if (this instanceof AsyncLogWriter) {
            return this;
        }
        return new AsyncLogWriter(this);
    }

    static class Combined extends SimpleLogWriter {

        private final LogWriter[] writers;

        public Combined(LogWriter... writers) {
            this.writers = writers;
        }

        @Override
        void hook(ShutdownHookRegistry reg) {
            for (LogWriter w : writers) {
                if (w instanceof SimpleLogWriter) {
                    ((SimpleLogWriter) w).hook(reg);
                }
            }
        }

        @Override
        public void write(CharSequence s) {
            for (LogWriter w : writers) {
                w.write(s);
            }
        }

        public String toString() {
            StringBuilder sb = new StringBuilder("CombinedLogWriter[");
            for (int i = 0; i < writers.length; i++) {
                sb.append(writers[i]);
                if (i != writers.length - 1) {
                    sb.append(", ");
                }
            }
            return sb.append(']').toString();
        }
    }

    static class FileWriter extends SimpleLogWriter {

        private final File file;
        private final OutputStream out;
        private static final Charset charset = Charset.forName("UTF-8");

        public FileWriter(File file, boolean gzip, int bufferSize) throws IOException {
            if (bufferSize <= 0) {
                bufferSize = 1024;
            }
            this.file = file;
            if (!file.exists()) {
                if (!file.createNewFile()) {
                    throw new IOException("Could not create " + file);
                }
            }
            OutputStream out = bufferSize == 1 ? new FileOutputStream(file, true)
                    : new BufferedOutputStream(new FileOutputStream(file, true), bufferSize);
            if (gzip) {
                out = new GZIPOutputStream(out, bufferSize, true);
            }
            this.out = out;
        }

        @Override
        void hook(ShutdownHookRegistry reg) {
            reg.add((Callable<?>) () -> {
                out.flush();
                out.close();
                return null;
            });
        }

        @Override
        public void write(CharSequence s) {
            try {
                CharsetEncoder enc = charset.newEncoder();
                ByteBuffer buf = enc.encode(CharBuffer.wrap(s));
                out.write(buf.array(), 0, buf.remaining());
                out.write('\n');
                out.flush();
            } catch (UnsupportedEncodingException ex) {
                Logger.getLogger(SimpleLogWriter.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(SimpleLogWriter.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        public String toString() {
            return "FileWriter{file=" + file + "}";
        }
    }

    static class AsyncLogWriter extends SimpleLogWriter {

        private final AtomicLinkedQueue<CharSequence> queue = new AtomicLinkedQueue<>();
        private final ExecutorService exe = Executors.newSingleThreadExecutor();
        private final Runner runner;
        private final OneThreadLatch latch = new OneThreadLatch();

        AsyncLogWriter(LogWriter writer) {
            exe.submit(runner = new Runner(queue, writer, latch));
            queue.onAdd(latch);
        }

        @Override
        public void write(CharSequence s) {
            queue.add(s);
        }

        void hook(ShutdownHookRegistry reg) {
            if (runner.writer instanceof SimpleLogWriter) {
                ((SimpleLogWriter) runner.writer).hook(reg);
            }
            reg.add((Runnable) () -> {
                runner.stop();
                exe.shutdown();
            });
        }

        public String toString() {
            return getClass().getName() + "{runner=" + runner + "}";
        }

        private static class Runner implements Runnable {

            private final AtomicLinkedQueue<CharSequence> queue;
            private final LogWriter writer;
            private final OneThreadLatch latch;

            public Runner(AtomicLinkedQueue<CharSequence> queue, LogWriter writer, OneThreadLatch latch) {
                this.queue = queue;
                this.writer = writer;
                this.latch = latch;
            }

            void flush(List<CharSequence> strings) throws InterruptedException {
                queue.drainTo(strings);
                for (CharSequence s : strings) {
                    writer.write(s);
                }
                strings.clear();
            }

            volatile boolean stopped;

            void stop() {
                stopped = true;
                try {
                    flush(new LinkedList<>());
                } catch (InterruptedException ex) {
                    Logger.getLogger(SimpleLogWriter.class.getName()).log(Level.SEVERE, null, ex);
                }
            }

            public String toString() {
                return "AsyncRunner{writer=" + writer + "}";
            }

            @Override
            public void run() {
                Thread.currentThread().setName("Bunyan-Java Log flush");
                Thread.currentThread().setPriority(Thread.NORM_PRIORITY - 2);
                List<CharSequence> strings = new LinkedList<>();
                for (;;) {
                    try {
                        latch.await();
                        flush(strings);
                        if (stopped) {
                            return;
                        }
                    } catch (InterruptedException ex) {
                        if (stopped) {
                            return;
                        }
                    }
                }
            }
        }
    }
}
