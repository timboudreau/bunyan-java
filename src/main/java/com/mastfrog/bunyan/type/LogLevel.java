package com.mastfrog.bunyan.type;

import com.mastfrog.bunyan.Log;

/**
 *
 * @author Tim Boudreau
 */
public interface LogLevel<T extends LogLevel> extends Comparable<LogLevel> {

    public String name();

    public int ordinal();
    
    public Log<T> logger(String name);
    
    public boolean isEnabled();
}
