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
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

/**
 *
 * @author Tim Boudreau
 */
public class Cursor implements AutoCloseable {

    private final File cursorFile;
    private long currentPosition;
    private SeekableByteChannel writeChannel;
    private final ByteBuffer cursorBuffer = ByteBuffer.allocate(8);

    public Cursor(File cursorFile) throws IOException {
        this.cursorFile = cursorFile;
        currentPosition = readCursorFile(cursorFile);
    }

    private long readCursorFile(File cursorFile) throws IOException {
        if (cursorFile.exists() && cursorFile.length() >= 8) {
            try (final FileChannel rc = new FileInputStream(cursorFile).getChannel()) {
                ByteBuffer buf = ByteBuffer.allocate(8);
                rc.read(buf);
                buf.flip();
                long result = buf.getLong();
                System.out.println("Read cursor pos from " + cursorFile + ": " + result);
                return result;
            }
        }
        return 0;
    }

    public long position() {
        return currentPosition;
    }

    public synchronized boolean isOpen() {
        return writeChannel != null && writeChannel.isOpen();
    }

    public boolean isFinished(File logFile) throws IOException {
        return currentPosition >= Files.size(logFile.toPath());
    }

    private synchronized SeekableByteChannel channel() throws IOException {
        if (writeChannel == null) {
            writeChannel = Files.newByteChannel(cursorFile.toPath(), StandardOpenOption.WRITE, StandardOpenOption.CREATE);
        }
        return writeChannel;
    }

    public synchronized void update(long position) throws IOException {
        System.out.println(" UPDATE " + cursorFile.getName() + " to " + position);
        cursorBuffer.putLong(position);
        currentPosition = position;
        System.out.println("  --write to cursor file " + cursorFile + " - " + position);
        cursorBuffer.flip();
        SeekableByteChannel channel = channel();
        channel.position(0);
        channel.write(cursorBuffer);
        cursorBuffer.rewind();
    }

    public synchronized void close() throws IOException {
        if (writeChannel != null) {
            writeChannel.close();
            writeChannel = null;
        }
    }
}
