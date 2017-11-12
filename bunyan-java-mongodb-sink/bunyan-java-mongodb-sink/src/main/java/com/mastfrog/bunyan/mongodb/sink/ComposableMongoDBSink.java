package com.mastfrog.bunyan.mongodb.sink;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mastfrog.bunyan.MultiLogSink;
import com.mastfrog.bunyan.type.LogLevel;
import java.util.Map;
import javax.inject.Inject;

/**
 * Wrapper for MongoDBLogSink for use with MultiLogSink.
 *
 * @author Tim Boudreau
 */
public final class ComposableMongoDBSink extends MultiLogSink.Sink {

    private final MongoDBLogSink mongo;

    @Inject
    public ComposableMongoDBSink(MultiLogSink multi, MongoDBLogSink mongo) {
        super(multi);
        this.mongo = mongo;
    }


    @Override
    public void push(LogLevel level, Map<String, Object> logrecord, ObjectMapper mapper) {
        mongo.doPush(level, logrecord, mapper);
    }
}
