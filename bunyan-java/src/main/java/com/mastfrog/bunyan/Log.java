package com.mastfrog.bunyan;

import com.mastfrog.bunyan.type.LogLevel;

/**
 * A thing which writes one line of JSON logging
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
