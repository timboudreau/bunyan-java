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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 *
 * @author Tim Boudreau
 */
final class CursorAndLogFile implements Comparable<CursorAndLogFile> {

    public final File cursorFile;
    public final File logFile;

    public CursorAndLogFile(File cursorFile, File logFile) {
        this.cursorFile = cursorFile;
        this.logFile = logFile;
    }

    public Cursor cursor() throws IOException {
        return new Cursor(cursorFile);
    }

    @Override
    public int compareTo(CursorAndLogFile o) {
        return logFile.getName().compareTo(o.logFile.getName());
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CursorAndLogFile && ((CursorAndLogFile) o).equals(logFile);
    }

    @Override
    public int hashCode() {
        return logFile.getName().hashCode() * 73;
    }

    @Override
    public String toString() {
        return logFile.getName();
    }

    public CursorAndLogFile nextLogFiles() {
        return RecoveryFiles.nextLogFiles(logFile);
    }

    public void delete() throws IOException {
        Path a = cursorFile.toPath();
        Path b = logFile.toPath();
        if (Files.exists(a)) {
            Files.delete(a);
        }
        if (Files.exists(b)) {
            Files.delete(b);
        }
    }

}
