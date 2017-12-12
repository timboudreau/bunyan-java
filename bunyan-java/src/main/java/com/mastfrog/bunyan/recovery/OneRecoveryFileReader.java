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

import com.mastfrog.util.Exceptions;
import com.mastfrog.util.streams.ContinuousLineStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Reads a log file and updates a cursor. Note: Does NOT follow the contract of
 * Iterable (same iterator state every time), but implements it for convenience.
 *
 * @author Tim Boudreau
 */
public class OneRecoveryFileReader implements AutoCloseable {

    private final Cursor cursor;
    private final ContinuousLineStream lines;
    private final CursorAndLogFile files;
    private long bytesRead;
    private Runnable onClose;

    OneRecoveryFileReader(Cursor cursor, ContinuousLineStream readStream, CursorAndLogFile files) throws IOException {
        System.out.println("CREATE READER " + files);
        this.cursor = cursor;
        this.lines = readStream;
        this.files = files;
        bytesRead = cursor.position();
    }

    void onClose(Runnable onClose) {
        this.onClose = onClose;
        if (!lines.isOpen()) {
            onClose.run();
        }
    }

    Object state() {
        try {
            return files.logFile.getPath() + ":" + cursor.position() + ":" + lines.isOpen() + ":" + (lines.isOpen() ? lines.available() : -1);
        } catch (IOException ex) {
            return Exceptions.chuck(ex);
        }
    }

    public long position() {
        return cursor.position();
    }

    boolean isOpen() {
        return lines.isOpen();
    }

    public CursorAndLogFile files() {
        return files;
    }

    public static OneRecoveryFileReader from(CursorAndLogFile files) throws IOException {
        Cursor cursor = files.cursor();
        long pos = cursor.position();
        if (pos >= files.logFile.length()) {
            return null;
        }
        if (files.logFile.length() == 0) {
            return null;
        }
        ContinuousLineStream readStream = ContinuousLineStream.of(files.logFile);
        if (pos > 0) {
            readStream.position(pos);
        }
        return new OneRecoveryFileReader(cursor, readStream, files);
    }

    private ProcessableLineImpl prevLine;

    public synchronized ProcessableLine nextLine(OnBeforeRead check) throws IOException {
        if (prevLine != null) {
            return prevLine;
        }
        check.onBeforeRead(files.logFile, cursor.position());
        CharSequence ln = lines.nextLine();
        if (ln == null) {
            return null;
        }
        return prevLine = new ProcessableLineImpl(ln.toString());
    }

    public boolean hasNext() throws IOException {
        synchronized (this) {
            if (prevLine != null) {
                return true;
            }
        }
        if (lines.isOpen()) {
            return lines.hasMoreLines();
        }
        return false;
    }

    public boolean isFinished() throws IOException {
        return !Files.exists(files.logFile.toPath())
                || cursor.isFinished(files.logFile);
    }

    private boolean closed;

    @Override
    public void close() throws Exception {
        if (closed) {
            return;
        }
        _close();
    }

    private void _close() throws Exception {
        System.out.println(" -- CLOSE " + files.logFile.getName());
        try {
            lines.close();
        } finally {
            try {
                cursor.close();
            } finally {
                closed = true;
                if (onClose != null) {
                    onClose.run();
                }
            }
        }
    }

    public void close(boolean delete) throws Exception {
        if (closed) {
            return;
        }
        boolean done = !hasNext() || cursor.isFinished(files.logFile);
        _close();
        if (done && delete) {
            System.out.println("DELETE " + files.logFile.getName());
            files.delete();
        } else if (!done && delete) {
            System.out.println("NOT DELETING - cursor position " + cursor.position() + " file size " + files.logFile.length());
        }
    }

    class ProcessableLineImpl implements ProcessableLine {

        final String line;
        private boolean consumed;

        public ProcessableLineImpl(String line) {
            this.line = line;
        }

        public String toString() {
            return line;
        }

        public void consumed() throws IOException {
            if (!consumed) {
                consumed = true;
                synchronized (OneRecoveryFileReader.this) {
                    if (prevLine == this) {
                        prevLine = null;
                    }
                    if (line != null) {
                        bytesRead += line.getBytes(StandardCharsets.UTF_8).length + 1;
                        cursor.update(bytesRead);
                    }
                }
            }
        }

        @Override
        public File file() {
            return files.logFile;
        }
    }
}
