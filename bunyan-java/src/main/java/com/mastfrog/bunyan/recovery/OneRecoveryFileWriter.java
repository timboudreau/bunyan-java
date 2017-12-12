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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mastfrog.util.function.FunctionalLock;
import com.mastfrog.util.function.ThrowingRunnable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.Map;

/**
 *
 * @author Tim Boudreau
 */
final class OneRecoveryFileWriter implements AutoCloseable {

    private static final byte[] NEWLINE = {'\n'};
    private final FileChannel channel;
    private final ObjectMapper mapper;
    private final ThrowingRunnable onClose;
    private long position;
    private final CursorAndLogFile files;
    private final FunctionalLock funlock = new FunctionalLock();

    public OneRecoveryFileWriter(CursorAndLogFile files, ObjectMapper mapper, ThrowingRunnable onClose) throws IOException {
        System.out.println("CREATE WRITER " + files);
        this.files = files;
        this.mapper = mapper;
        this.onClose = onClose;
        channel = FileChannel.open(files.logFile.toPath(), StandardOpenOption.APPEND, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        position = channel.size();
    }

    public CursorAndLogFile files() {
        return files;
    }

    boolean isOpen() {
        return channel.isOpen();
    }

    public void write(Map<String, Object> logRecord) throws JsonProcessingException, IOException, Exception {
        funlock.underWriteLock(() -> {
            byte[] b = mapper.writeValueAsBytes(logRecord);
            channel.write(ByteBuffer.wrap(b));
            channel.write(ByteBuffer.wrap(NEWLINE));
            position += b.length + 1;
        });
    }

    public long position() {
        return position;
    }

    @Override
    public void close() throws Exception {
        System.out.println(" --close recovery writer");
        funlock.underReadLock(() -> {
            channel.close();
            onClose.run();
        });
    }
}
