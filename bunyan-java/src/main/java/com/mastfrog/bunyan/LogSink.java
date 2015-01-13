package com.mastfrog.bunyan;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.name.Named;
import com.mastfrog.bunyan.type.LogLevel;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;

/**
 *
 * @author Tim Boudreau
 */
class LogSink {

    private final ObjectMapper mapper;
    private final LogWriter writer;

    @Inject
    LogSink(@Named(LoggingModule.GUICE_BINDING_OBJECT_MAPPER) ObjectMapper mapper, LogWriter writer, LoggingConfig config) {
        this.mapper = mapper;
        this.writer = writer;
    }

    void push(LogLevel level, Map<String, Object> log) {
        try {
            String s = mapper.writeValueAsString(log);
            writer.write(s);
        } catch (JsonProcessingException ex) {
            // Give up
            Logger.getLogger(LogSink.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
