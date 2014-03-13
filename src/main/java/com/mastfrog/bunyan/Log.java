package com.mastfrog.bunyan;

import com.mastfrog.bunyan.type.LogLevel;

/**
 *
 * @author Tim Boudreau
 */
public interface Log<T extends LogLevel> extends AutoCloseable {

    public T level();

    public Log<T> message(String msg);

    public Log<T> add(Object o);

    public Log<T> add(String name, Object value);

    public Log<T> add(Throwable t);

    @Override
    public void close();

}
