package com.mastfrog.bunyan.mongodb.sink;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mastfrog.bunyan.LogSink;
import static com.mastfrog.bunyan.LoggingModule.GUICE_BINDING_OBJECT_MAPPER;
import com.mastfrog.bunyan.type.LogLevel;
import com.mastfrog.giulius.ShutdownHookRegistry;
import com.mastfrog.settings.Settings;
import com.mastfrog.util.ConfigurationError;
import com.mongodb.ConnectionString;
import com.mongodb.MongoCommandException;
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
import com.mongodb.connection.ConnectionPoolSettings;
import com.mongodb.connection.SocketSettings;
import com.mongodb.event.ConnectionAddedEvent;
import com.mongodb.event.ConnectionCheckedInEvent;
import com.mongodb.event.ConnectionCheckedOutEvent;
import com.mongodb.event.ConnectionPoolClosedEvent;
import com.mongodb.event.ConnectionPoolListener;
import com.mongodb.event.ConnectionPoolOpenedEvent;
import com.mongodb.event.ConnectionPoolWaitQueueEnteredEvent;
import com.mongodb.event.ConnectionPoolWaitQueueExitedEvent;
import com.mongodb.event.ConnectionRemovedEvent;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.bson.Document;
import static com.mastfrog.util.Checks.greaterThanZero;
import static com.mastfrog.util.Checks.greaterThanZero;
import static com.mastfrog.util.Checks.greaterThanOne;

/**
 * An implementation of LogSink which writes to MongoDB, using
 * WriteConcern.UNACKNOWLEDGED by default for performance.  <i>All of the
 * constants on this class must be set in settings, system properties or
 * environment variables except for user and password, which are optional.</i>.
 *
 * @author Tim Boudreau
 */
@Singleton
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
    public static final String SETTINGS_KEY_LOG_COLLECTION = "bunyan.mongo.collection.name";
    /**
     * The maximum documents to put in the (capped) collection - ignored unless
     * the collection does not yet exist.
     */
    public static final String SETTINGS_KEY_MAX_DOCUMENTS = "bunyan.mongo.max.documents";

    public static final String SETTINGS_KEY_MONGO_BUNYAN_USER = "bunyan.mongo.user";
    public static final String SETTINGS_KEY_MONGO_BUNYAN_PASSWORD = "bunyan.mongo.password";
    public static final String SETTINGS_KEY_MONGO_BUNYAN_COLLECTION_SIZE = "bunyan.mongo.collection.size";
    public static final String SETTINGS_KEY_MONGO_BUNYAN_MAX_WAIT_QUEUE_SIZE = "bunyan.mongo.max.waitqueue.max";
    public static final String SETTINGS_KEY_MONGO_BUNYAN_MAX_WAIT_QUEUE_TIME = "bunyan.mongo.max.waitqueue.maxseconds";
    public static final String SETTINGS_KEY_MONGO_BUNYAN_MAX_CONNECTIONS = "bunyan.mongo.max.connections";
    public static final long DEFAULT_MONGO_CAPPED_COLLECTION_SIZE = 1024 * 1024 * 1024 * 2;
    /**
     * The default cap.
     */
    public static final long DEFAULT_MAX_DOCUMENTS = 60 * 1024;
    final MongoClient client;
    final long cappedCollectionSize;
    private final boolean outUnsafe;
    private final ShutdownHookRegistry reg;
    private final String logCollection;
    private final String dbName;

    @Inject
    public MongoDBLogSink(@Named(GUICE_BINDING_OBJECT_MAPPER) ObjectMapper mapper, @Named(SETTINGS_KEY_BUNYAN_MONGO_CONNECTION_STRING) String mongoUrl,
            Settings settings, ShutdownHookRegistry reg, @Named(SETTINGS_KEY_LOG_COLLECTION) String logCollection, @Named(SETTINGS_KEY_DATABASE_NAME) String dbName) {
        outUnsafe = settings.getBoolean("bunyan.system.out", false) || settings.getBoolean("bunyan.system.err", false);
        this.reg = reg;
        this.dbName = dbName;
        this.logCollection = logCollection;
        WriteConcern writeConcern = WriteConcern.valueOf(settings.getString("bunyan.mongo.write.concern", "UNACKNOWLEDGED"));

        cappedCollectionSize = greaterThanZero(SETTINGS_KEY_MONGO_BUNYAN_COLLECTION_SIZE,
                settings.getLong(SETTINGS_KEY_MONGO_BUNYAN_COLLECTION_SIZE, DEFAULT_MONGO_CAPPED_COLLECTION_SIZE));

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
                .applicationName(settings.getString("application.name", MongoDBLogSink.class.getSimpleName()))
                .connectionPoolSettings(ConnectionPoolSettings.builder()
                        .addConnectionPoolListener(new ConnectionPoolListener() {
                            @Override
                            public void connectionPoolOpened(ConnectionPoolOpenedEvent cpoe) {
                                System.out.println("connection pool opened");
                            }

                            @Override
                            public void connectionPoolClosed(ConnectionPoolClosedEvent cpce) {
                                System.out.println("connection pool closed");
                            }

                            @Override
                            public void connectionCheckedOut(ConnectionCheckedOutEvent ccoe) {
                                System.out.println("connection checked out");
                            }

                            @Override
                            public void connectionCheckedIn(ConnectionCheckedInEvent ccie) {
                                System.out.println("connection checked in");
                            }

                            @Override
                            public void waitQueueEntered(ConnectionPoolWaitQueueEnteredEvent cpwqee) {
                                System.out.println("wait queue entered");
                            }

                            @Override
                            public void waitQueueExited(ConnectionPoolWaitQueueExitedEvent cpwqee) {
                                System.out.println("wait queue exited");
                            }

                            @Override
                            public void connectionAdded(ConnectionAddedEvent cae) {
                                System.out.println("connection added");
                            }

                            @Override
                            public void connectionRemoved(ConnectionRemovedEvent cre) {
                                System.out.println("connection removed");
                            }
                        })
                        .applyConnectionString(new ConnectionString(mongoUrl))
                        .maxSize(settings.getInt(SETTINGS_KEY_MONGO_BUNYAN_MAX_CONNECTIONS, settings.getInt("eventThreads", 16)))
                        .maxWaitQueueSize(settings.getInt(SETTINGS_KEY_MONGO_BUNYAN_MAX_WAIT_QUEUE_SIZE, 16384))
                        .maxWaitTime(settings.getInt(SETTINGS_KEY_MONGO_BUNYAN_MAX_WAIT_QUEUE_TIME, 60), TimeUnit.SECONDS)
                        .build())
                .heartbeatSocketSettings(new SocketSettings.Builder().connectTimeout(1, TimeUnit.MINUTES).build())
                .clusterSettings(
                        ClusterSettings.builder().applyConnectionString(new ConnectionString(mongoUrl))
                                .mode(ClusterConnectionMode.SINGLE).build());

        if (credential != null) {
            mongoSettings.credentialList(Arrays.asList(credential));
        }

        client = MongoClients.create(mongoSettings.build());
        reg.add(client);
        MongoDatabase db = client.getDatabase(dbName);
        long cap = settings.getLong(SETTINGS_KEY_MAX_DOCUMENTS, DEFAULT_MAX_DOCUMENTS);
        db.createCollection(logCollection, new CreateCollectionOptions().capped(true).maxDocuments(cap).sizeInBytes(cappedCollectionSize), CB);
        this.mapper = mapper;
    }

    private MongoCollection<Document> collection() {
        return client.getDatabase(dbName).getCollection(logCollection);
    }

    @Override
    public void push(LogLevel level, Map<String, Object> logrecord) {
        if (reg.isRunningShutdownHooks()) {
            try {
                System.out.println("Cannot log - shutting down: " + mapper.writeValueAsString(logrecord));
                return;
            } catch (JsonProcessingException ex) {
                if (!outUnsafe) {
                    ex.printStackTrace();
                }
            }
        }
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
            collection().insertOne(new Document(logRecord), CB);
        } catch (com.mongodb.MongoWaitQueueFullException ex) {
            if (!outUnsafe) {
                try {
                    //                ex.printStackTrace();
                    System.out.println("Cannot log: " + mapper.writeValueAsString(logRecord));
                } catch (JsonProcessingException ex1) {
                    ex1.printStackTrace();
                }
            }
        } catch (Exception ex) {
            // We are inside the logger here, nothing can be done
//            Logger.getLogger(MongoDBLogSink.class.getName()).log(Level.SEVERE, null, ex);
            if (!outUnsafe) {
//                ex.printStackTrace();
            }
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
                if (thrwbl instanceof MongoCommandException) {
                    MongoCommandException e = (MongoCommandException) thrwbl;
                    if (e.getErrorCode() == 48) {
                        // Namespace exists - tried to create a collection that
                        // already exists - ok
                        // Do not log (even to stderr) here , since we are still
                        // initializing the logger and stdout may be rerouted back to
                        // this object while we are in its constructor
                        return;
                    }
                }
                thrwbl.printStackTrace(System.err);
            }
        }
    }
}
