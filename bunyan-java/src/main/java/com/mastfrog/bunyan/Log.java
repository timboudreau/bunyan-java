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

import com.mastfrog.bunyan.type.LogLevel;

/**
 * A thing which writes one line of JSON logging.
 *
 * @author Tim Boudreau
 */
public interface Log<T extends LogLevel> extends AutoCloseable {

    public T level();

    /**
     * Set the message for this log record. Additional strings passed to this
     * method or add(Object) will be concatenated to this string.
     *
     * @param msg
     * @return this
     */
    public Log<T> message(String msg);

    /**
     * Add some object to the log record. If it is serializable by Jackson, its
     * key/value pairs will be incorporated into this log record; if it is a
     * list, it will be turned into a Map&lt;Integer,Object&gt; and the same
     * done with it; if it is a string, it will be concatenated with the already
     * set message (if any) or will become the message.
     *
     * @param o An object
     * @return this
     */
    public Log<T> add(Object o);

    /**
     * Add a key value pair to this log record
     *
     * @param name The key
     * @param value the value
     * @return this
     */
    public Log<T> add(String name, Object value);

    public Log<T> addIfNotNull(String name, Object value);

    /**
     * Add an error. The error's message becomes the message of this log record;
     * its stack is serialized as JSON.
     *
     * @param t A throwable
     * @return this
     */
    public Log<T> add(Throwable t);

    /**
     * Write out this log record
     */
    @Override
    public void close();

}
