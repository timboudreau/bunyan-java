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
package com.mastfrog.bunyan;

import com.mastfrog.giulius.ShutdownHookRegistry;
import com.mastfrog.util.thread.BufferPool;
import com.mastfrog.util.thread.FactoryThreadLocal;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * High performance log writer with buffering - microbenchmarks show writing
 * 3,514,623 records in 15 seconds.
 *
 * @author Tim Boudreau
 */
class NioFileWriter extends SimpleLogWriter implements Callable<Void>, LogWriter.Bytes {

    private final FileChannel channel;
    private final Charset charset = Charset.forName("UTF-8");
    private final FactoryThreadLocal<CharsetEncoder> encoder;
    private final boolean synchronous;
    private final File file;
    private final BufferPool pool;

    NioFileWriter(File file, boolean synchronous, int bufferSize) throws IOException {
        pool = new BufferPool(bufferSize <= 0 ? 4096 : bufferSize);
        channel = FileChannel.open(file.toPath(), StandardOpenOption.CREATE,
                StandardOpenOption.APPEND, StandardOpenOption.WRITE);
        encoder = new FactoryThreadLocal<>(charset::newEncoder);
        this.synchronous = synchronous;
        this.file = file;
    }

    @Override
    void hook(ShutdownHookRegistry reg) {
        reg.add(this);
    }

    private CharBuffer toCharBuffer(CharSequence s) {
        if (s instanceof CharBuffer) {
            return (CharBuffer) s;
        }
        int len = s.length();
        CharBuffer result = CharBuffer.allocate(s.length() + 1);
        for (int i = 0; i < len; i++) {
            result.put(s.charAt(i));
        }
        result.put('\n');
        result.flip();
        return result;
    }

    @Override
    public void write(byte[] bytes) {
        try {
            int pos = 0;
            try (final BufferPool.BufferHolder q = pool.buffer()) {
                ByteBuffer buffer = q.buffer();
                while (pos < bytes.length) {
                    int remaining = bytes.length - pos;
                    int bufferRemaining = buffer.capacity() - buffer.position();
                    int writeCount = Math.min(remaining, bufferRemaining);
                    buffer.put(bytes, pos, writeCount);
                    pos += writeCount;
                    if (pos < bytes.length) {
                        buffer.flip();
                        channel.write(buffer);
                        buffer.rewind();
                    } else {
                        if (buffer.remaining() > 0) {
                            buffer.put((byte) '\n');
                        } else {
                            buffer.flip();
                            channel.write(buffer);
                            buffer.rewind();
                            buffer.put((byte) '\n');
                        }
                    }
                }
                if (synchronous) {
                    channel.force(true);
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    void flushBuffer(ByteBuffer buffer, CharsetEncoder enc, boolean finished) throws IOException {
        if (finished) {
            enc.flush(buffer);
        }
        buffer.flip();
        channel.write(buffer);
        buffer.rewind();
        if (synchronous) {
            channel.force(true);
        }
    }

    @Override
    public void write(CharSequence s) {
        CharsetEncoder enc = encoder.get();
        enc.reset();
        CharBuffer cb = toCharBuffer(s);
        CoderResult res = CoderResult.OVERFLOW;
        try (final BufferPool.BufferHolder q = pool.buffer()) {
            ByteBuffer buffer = q.buffer();
            while (res == CoderResult.OVERFLOW) {
                res = enc.encode(cb, buffer, true);
                if (res == CoderResult.OVERFLOW) {
                    flushBuffer(buffer, enc, false);
                } else if (res == CoderResult.UNDERFLOW) {
                    if (synchronous) {
                        flushBuffer(buffer, enc, true);
                    } else {
                        enc.flush(buffer);
                    }
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public String toString() {
        return getClass().getSimpleName() + "{ file=" + file + " }";
    }

    void flush() throws IOException {
        try {
            List<ByteBuffer> buffers = pool.awaitQuiet();
            for (ByteBuffer buf : buffers) {
                buf.flip();
                channel.write(buf);
            }
            pool.close();
            channel.force(true);
        } catch (InterruptedException ex) {
            Logger.getLogger(SimpleLogWriter.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public Void call() throws Exception {
        try {
            flush();
        } finally {
            channel.close();
        }
        return null;
    }

}
