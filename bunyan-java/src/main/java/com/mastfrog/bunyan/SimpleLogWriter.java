package com.mastfrog.bunyan;

import com.mastfrog.giulius.ShutdownHookRegistry;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

/**
 *
 * @author Tim Boudreau
 */
public class SimpleLogWriter implements LogWriter {

    public void write(String s) {
        System.out.println(s);
    }

    public static LogWriter combine(LogWriter... writers) {
        return new Combined(writers);
    }

    public static LogWriter forFile(File f, boolean gzip) throws IOException {
        return new FileWriter(f, gzip);
    }

    public static LogWriter async(LogWriter writer) {
        return writer instanceof AsyncLogWriter ? (LogWriter) writer : new AsyncLogWriter(writer);
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
        public void write(String s) {
            for (LogWriter w : writers) {
                w.write(s);
            }
        }
    }

    static class FileWriter extends SimpleLogWriter {

        private final File file;
        private final OutputStream out;

        public FileWriter(File file, boolean gzip) throws IOException {
            this.file = file;
            if (!file.exists()) {
                if (!file.createNewFile()) {
                    throw new IOException("Could not create " + file);
                }
            }
            OutputStream out = new BufferedOutputStream(new FileOutputStream(file, true), 1024);
            if (gzip) {
                out = new GZIPOutputStream(out, 1024, true);
            }
            this.out = out;
        }

        @Override
        public String toString() {
            return "log file " + file;
        }

        @Override
        void hook(ShutdownHookRegistry reg) {
            reg.add(new Runnable() {
                @Override
                public void run() {
                    try {
                        out.flush();
                        out.close();
                    } catch (IOException ex) {
                        Logger.getLogger(SimpleLogWriter.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }

            });
        }

        @Override
        public void write(String s) {
            try {
                out.write(s.getBytes("UTF-8"));
                out.write('\n');
                out.flush();
            } catch (UnsupportedEncodingException ex) {
                Logger.getLogger(SimpleLogWriter.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(SimpleLogWriter.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    static class AsyncLogWriter extends SimpleLogWriter {

        private final LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<String>();
        private final ExecutorService exe = Executors.newSingleThreadExecutor();
        private final Runner runner;

        AsyncLogWriter(LogWriter writer) {
            exe.submit(runner = new Runner(queue, writer));
        }

        @Override
        public void write(String s) {
            queue.offer(s);
        }

        void hook(ShutdownHookRegistry reg) {
            reg.add(new Runnable() {

                @Override
                public void run() {
                    runner.stop();
                    exe.shutdown();
                }
            });
            if (runner.writer instanceof SimpleLogWriter) {
                ((SimpleLogWriter) runner.writer).hook(reg);
            }
        }

        private static class Runner implements Runnable {

            private final LinkedBlockingQueue<String> queue;
            private final LogWriter writer;

            public Runner(LinkedBlockingQueue<String> queue, LogWriter writer) {
                this.queue = queue;
                this.writer = writer;
            }

            void flush(List<String> strings) throws InterruptedException {
                String first = queue.take();
                strings.add(first);
                queue.drainTo(strings);
                for (String s : strings) {
                    writer.write(s);
                }
                strings.clear();
            }

            volatile boolean stopped;
            void stop() {
                stopped = true;
                for (String s : queue) {
                    writer.write(s);
                }
            }

            @Override
            public void run() {
                Thread.currentThread().setName("Bunyan-Java Log flush");
                Thread.currentThread().setPriority(Thread.NORM_PRIORITY - 2);
                List<String> strings = new LinkedList<>();
                for (;;) {
                    try {
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
