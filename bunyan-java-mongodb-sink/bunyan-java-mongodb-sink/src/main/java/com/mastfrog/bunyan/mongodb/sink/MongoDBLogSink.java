package com.mastfrog.bunyan.mongodb.sink;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mastfrog.bunyan.LogSink;
import static com.mastfrog.bunyan.LoggingModule.GUICE_BINDING_OBJECT_MAPPER;
import com.mastfrog.bunyan.type.LogLevel;
import com.mastfrog.giulius.ShutdownHookRegistry;
import com.mastfrog.settings.Settings;
import com.mastfrog.util.ConfigurationError;
import com.mongodb.ConnectionString;
import com.mongodb.MongoCredential;
import com.mongodb.WriteConcern;
import com.mongodb.async.SingleResultCallback;
import com.mongodb.async.client.MongoClient;
import com.mongodb.async.client.MongoClientSettings;
import com.mongodb.async.client.MongoClients;
import com.mongodb.async.client.MongoCollection;
import com.mongodb.async.client.MongoDatabase;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.connection.ClusterConnectionMode;
import com.mongodb.connection.ClusterSettings;
import java.util.Arrays;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;
import javax.inject.Named;
import org.bson.Document;

/**
 * An implementation of LogSink which writes to MongoDB, using
 * WriteConcern.UNACKNOWLEDGED by default for performance.  <i>All of the
 * constants on this class must be set in settings, system properties or
 * environment variables except for user and password, which are optional.</i>.
 *
 * @author Tim Boudreau
 */
public final class MongoDBLogSink implements LogSink {

    private final ObjectMapper mapper;

    /**
     * The connect string per the mongo driver.
     */
    public static final String SETTINGS_KEY_BUNYAN_MONGO_CONNECTION_STRING = "bunyan.mongo.connect";
    /**
     * The database to write to.
     */
    public static final String SETTINGS_KEY_DATABASE_NAME = "bunyan.mongo.db";
    /**
     * The collection to write to.
     */
    public static final String SETTINGS_KEY_LOG_COLLECTION = "bunyan.mongo.collection";
    /**
     * The maximum documents to put in the (capped) collection - ignored unless
     * the collection does not yet exist.
     */
    public static final String SETTINGS_KEY_MAX_DOCUMENTS = "bunyan.mongo.max.documents";

    public static final String SETTINGS_KEY_MONGO_BUNYAN_USER = "bunyan.mongo.user";
    public static final String SETTINGS_KEY_MONGO_BUNYAN_PASSWORD = "bunyan.mongo.password";
    /**
     * The default cap.
     */
    public static final int DEFAULT_MAX_DOCUMENTS = 60 * 1024;
    final MongoClient client;
    final MongoDatabase db;
    final MongoCollection<Document> collection;

    @Inject
    public MongoDBLogSink(@Named(GUICE_BINDING_OBJECT_MAPPER) ObjectMapper mapper, @Named(SETTINGS_KEY_BUNYAN_MONGO_CONNECTION_STRING) String mongoUrl,
            Settings settings, ShutdownHookRegistry reg, @Named(SETTINGS_KEY_LOG_COLLECTION) String logCollection, @Named(SETTINGS_KEY_DATABASE_NAME) String dbName) {
        WriteConcern writeConcern = WriteConcern.valueOf(settings.getString("bunyan.mongo.write.concern", "UNACKNOWLEDGED"));

        String mongoUser = settings.getString(SETTINGS_KEY_MONGO_BUNYAN_USER);
        String mongoPassword = settings.getString(SETTINGS_KEY_MONGO_BUNYAN_PASSWORD);
        if ((mongoUser == null) != (mongoPassword == null)) {
            throw new ConfigurationError("Either both " + SETTINGS_KEY_MONGO_BUNYAN_USER + " and "
                    + SETTINGS_KEY_MONGO_BUNYAN_PASSWORD + " must be set, or neither may be.");
        }
        MongoCredential credential = null;
        if (mongoUser != null) {
            credential = MongoCredential.createCredential(mongoUser, dbName, mongoPassword.toCharArray());
        }

        MongoClientSettings.Builder mongoSettings = MongoClientSettings.builder()
                .writeConcern(writeConcern)
                .clusterSettings(
                        ClusterSettings.builder().applyConnectionString(new ConnectionString(mongoUrl))
                                .mode(ClusterConnectionMode.SINGLE).build());

        if (credential != null) {
            mongoSettings.credentialList(Arrays.asList(credential));
        }

        client = MongoClients.create(mongoSettings.build());
        reg.add(client);
        db = client.getDatabase(dbName);
        long cap = settings.getLong(SETTINGS_KEY_MAX_DOCUMENTS, DEFAULT_MAX_DOCUMENTS);
        db.createCollection(logCollection, new CreateCollectionOptions().capped(true).maxDocuments(cap), CB);
        collection = db.getCollection(logCollection);
        this.mapper = mapper;
    }

    @Override
    public void push(LogLevel level, Map<String, Object> logrecord) {
        doPush(level, logrecord, mapper);
    }

    void doPush(LogLevel level, Map<String, Object> logRecord, ObjectMapper mapper) {
        try {
            logRecord = mapper.readValue(mapper.writeValueAsBytes(logRecord), TR);
            // Ensure if by chance there is a field named _id, we do not accidentally
            // write it as the real ID - it may occur multiple times
            if (logRecord.containsKey("_id")) {
                Object o = logRecord.get("_id");
                logRecord.remove("_id");
                logRecord.put("__id", o);
            }
            collection.insertOne(new Document(logRecord), CB);
        } catch (Exception ex) {
            // We are inside the logger here, nothing can be done
            Logger.getLogger(MongoDBLogSink.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    static final TR TR = new TR();

    static final class TR extends TypeReference<Map<String, Object>> {
    }

    static final CB CB = new CB();

    static final class CB implements SingleResultCallback<Void> {

        @Override
        public void onResult(Void t, Throwable thrwbl) {
            // do nothing
            if (thrwbl != null) {
                thrwbl.printStackTrace(System.err);
            }
        }
    }
}
