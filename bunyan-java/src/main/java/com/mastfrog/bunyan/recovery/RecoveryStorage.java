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
package com.mastfrog.bunyan.recovery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mastfrog.bunyan.LogSink;
import com.mastfrog.bunyan.recovery.RecoveryFilesReader.Processor;
import com.mastfrog.bunyan.type.LogLevel;
import com.mastfrog.util.collections.StringObjectMap;
import com.mastfrog.util.function.ThrowingConsumer;
import java.io.File;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/**
 *
 * @author Tim Boudreau
 */
public class RecoveryStorage implements LogSink {

    private final RecoveryFiles files;
    private final ObjectMapper mapper;
    private volatile OneRecoveryFileWriter writer;
    private final AtomicBoolean replaceWriterAfterNextWrite = new AtomicBoolean();
    private final AtomicReference<CursorAndLogFile> logFiles = new AtomicReference<>();

    public RecoveryStorage(RecoveryFiles files, ObjectMapper mapper) {
        this.files = files;
        this.mapper = mapper;
        logFiles.set(files.newLogFiles());
    }

    public void open() {
        logFiles.set(files.newLogFiles());
    }

    public synchronized void close() throws Exception {
        if (writer != null) {
            writer.close();
        }
    }

    public String toString() {
        return "RecoveryStorage: " + files.dir;
    }

    public boolean recover(ThrowingConsumer<Map<String, Object>> lineReceiver, Predicate<Exception> shouldStopFor) throws Exception {
        System.out.println("ENTER RECOVERY");
        RecoveryFilesReader reader = new RecoveryFilesReader(files);
        try (Processor p = reader.newProcessor()) {
            System.out.println("START LOOP");
            while (p.hasNext()) {
                ProcessableLine pl = p.next(RecoveryStorage.this::onBeforeRead);
                if (pl == null) {
                    System.out.println("NULL LINE, BREAK " + p);
                    break;
                }
                Map<String, Object> m = mapper.readValue(pl.text(), StringObjectMap.class);
                try {
                    System.out.println("APPLY " + m);
                    lineReceiver.apply(m);
                } catch (Exception ex) {
                    if (shouldStopFor.test(ex)) {
                        System.out.println("  EXIT RECOVERY UNFINISHED for " + ex);
                        return false;
                    } else {
                        ex.printStackTrace();
                        pl.consumed();
                    }
                }
                pl.consumed();
            }
        }
        System.out.println("EXIT RECOVERY ");
        return true;
    }

    void onBeforeRead(File file, long position) {
        CursorAndLogFile calf = logFiles.get();
        if (file.equals(calf.logFile)) {
            OneRecoveryFileWriter writer;
            synchronized (this) {
                writer = this.writer;
            }
            if (writer != null && writer.isOpen() && file.equals(writer.files().logFile)) {
                replaceWriterAfterNextWrite.set(true);
                if (position >= writer.position()) {
                    try {
                        // No terribly good options here if we want to keep
                        // writes from blocking on reads
                        for (int i = 0; i < 10; i++) {
                            System.out.println("WRITING TO SAME FILE AS READING - TRY TO GET OUT OF THE WAY");
                            Thread.sleep(150);
                            if (position < writer.position()) {
                                break;
                            }
                        }
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }
    }

    public void write(Map<String, Object> line) throws Exception {
        OneRecoveryFileWriter localWriter = this.writer;
        if (localWriter == null) {
            synchronized (this) {
                localWriter = this.writer;
                if (localWriter == null) {
                    final OneRecoveryFileWriter[] ref = new OneRecoveryFileWriter[1];
                    CursorAndLogFile nxt = files.newLogFiles();
                    logFiles.set(nxt);
                    this.writer = localWriter = new OneRecoveryFileWriter(nxt, mapper, () -> {
                        synchronized (RecoveryStorage.this) {
                            if (this.writer == ref[0]) {
                                this.writer = null;
                                logFiles.set(nxt.nextLogFiles());
                            }
                        }
                    });
                    ref[0] = localWriter;
                }
            }
        }
        try {
            localWriter.write(line);
        } finally {
            if (replaceWriterAfterNextWrite.compareAndSet(true, false)) {
                localWriter.close(); // will null writer variable
            }
        }
    }

    @Override
    public void push(LogLevel level, Map<String, Object> logrecord) {
        try {
            write(logrecord);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
