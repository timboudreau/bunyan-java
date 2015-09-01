package com.mastfrog.bunyan;

import com.google.inject.ImplementedBy;
import com.mastfrog.bunyan.type.LogLevel;
import java.util.Map;

/**
 * Bind this instead of LogWriter if you want to receive log records as
 * objects (say, for sending over the wire as BSON) instead of strings.
 *
 * @author Tim Boudreau
 */
@ImplementedBy(DefaultLogSink.class)
public interface LogSink {
    /**
     * Called to write a new log record.  Must be thread-safe.
     * 
     * @param level The log level.
     * @param logrecord The log record
     */
    void push(LogLevel level, Map<String, Object> logrecord);
}
