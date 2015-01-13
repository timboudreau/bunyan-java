package com.mastfrog.bunyan;

import com.google.inject.ImplementedBy;

/**
 *
 * @author Tim Boudreau
 */
@ImplementedBy(DefaultLogWriter.class)
public interface LogWriter {

    public void write(String s);
}
