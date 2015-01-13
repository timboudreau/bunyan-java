package com.mastfrog.bunyan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.name.Named;
import static com.mastfrog.bunyan.LoggingModule.SETTINGS_KEY_LOG_HOSTNAME;
import static com.mastfrog.bunyan.LoggingModule.SETTINGS_KEY_LOG_LEVEL;
import com.mastfrog.settings.Settings;
import com.mastfrog.util.ConfigurationError;
import com.mastfrog.util.collections.CollectionUtils;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Logging configuration. Determines a few things for loggers like the default
 * level (controlled with the setting <code>log.level</code> in the default
 * <a href="http://timboudreau.com/builds/job/mastfrog-parent/lastSuccessfulBuild/artifact/giulius-modules/giulius-parent/giulius-settings/target/apidocs/com/mastfrog/settings/Settings.html"><code>Settings</code></a>
 * injected by Guice.
 *
 * @author Tim Boudreau
 */
@Singleton
public class LoggingConfig {

    private final String hostname;
    private final LogWriter writer;
    private final int minLevel;
    private final ObjectMapper mapper;

    public static final String LEVEL_DEBUG = "debug";
    public static final String LEVEL_INFO = "info";
    public static final String LEVEL_FATAL = "fatal";
    public static final String LEVEL_ERROR = "error";
    public static final String LEVEL_WARNING = "warn";
    public static final String LEVEL_TRACE = "trace";

    public static boolean checkLevelName(String level) {
        switch (level) {
            case LEVEL_TRACE:
            case LEVEL_INFO:
            case LEVEL_WARNING:
            case LEVEL_ERROR:
            case LEVEL_DEBUG:
            case LEVEL_FATAL:
                return true;
            default:
                return false;
        }
    }

    public static void throwIfInvalidLevelName(String level) {
        if (!checkLevelName(level)) {
            throw new IllegalArgumentException("Invalid log level " + level + " - valid values are "
                    + LEVEL_FATAL + "," + LEVEL_DEBUG + "," + LEVEL_INFO + "," + LEVEL_ERROR + ","
                    + LEVEL_WARNING + " or" + LEVEL_TRACE);
        }
    }

    @Inject
    LoggingConfig(Settings settings, @Named(LoggingModule.GUICE_BINDING_OBJECT_MAPPER) ObjectMapper mapper, 
            LogWriter writer) throws IOException {
        hostname = hostname(settings);
        String minLogLevel = settings.getString(SETTINGS_KEY_LOG_LEVEL);
        int level = -1;
        if (minLogLevel != null) {
            switch (minLogLevel) {
                case LEVEL_DEBUG:
                    level = 20;
                    break;
                case LEVEL_FATAL:
                    level = 60;
                    break;
                case LEVEL_ERROR:
                    level = 50;
                    break;
                case LEVEL_WARNING:
                    level = 40;
                    break;
                case LEVEL_INFO:
                    level = 30;
                    break;
                case LEVEL_TRACE:
                    level = 10;
                    break;
                default:
                    throw new ConfigurationError("Mysterious log level '" + minLogLevel
                            + "' not one of " + Arrays.asList(LEVEL_DEBUG,
                                    LEVEL_FATAL, LEVEL_ERROR, LEVEL_WARNING, LEVEL_INFO, LEVEL_TRACE));
            }
        }
        if (level == -1) {
            level = 10;
        }
        minLevel = level;
        this.mapper = mapper;
        this.writer = writer;
    }

    ObjectMapper mapper() {
        return mapper;
    }

    LogWriter writer() {
        return writer;
    }

    public String hostname() {
        return hostname;
    }

    String hostname(Settings settings) {
        String hostname = settings.getString(SETTINGS_KEY_LOG_HOSTNAME);
        if (hostname != null) {
            return hostname;
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            try {
                return findHostnameFromNetworkAddress(settings);
            } catch (SocketException ex) {
                return "localhost";
            }
        }
    }

    static String findIpV4Address(NetworkInterface iface) {
        // XXX this is dangerous if we need ipv6 - but it is only called
        // if the ingest server's url host is "localhost"
        String result = null;
        for (InetAddress addr : CollectionUtils.toIterable(iface.getInetAddresses())) {
            String hn = addr.getHostAddress();
            if (!hn.contains(":")) {
                result = hn;
            }
        }
        return result;
    }

    static String findHostnameFromNetworkAddress(Settings settings) throws SocketException {
        // For localhost urls (demo vm), try to find an IPv4 address - localhost definitely
        // won't work, and we need something that is an external interface
        // Preferr IPv4 since that is usually what you get
        String result = null;
        String preferred = settings.getString("network.interface.for.urls");
        if (preferred != null) {
            if (NetworkInterface.getByName(preferred) == null) {
                preferred = null;
            }
        }
        result = "localhost";
        NetworkInterface bestMatch = null;
        for (NetworkInterface ni : CollectionUtils.<NetworkInterface>toIterable(NetworkInterface.getNetworkInterfaces())) {
            if (!ni.isLoopback()) {
                if (ni.isUp()) {
                    if (!ni.isVirtual() && !ni.isPointToPoint()) {
                        if (preferred != null && preferred.equalsIgnoreCase(ni.getName()) && ni.getInetAddresses().hasMoreElements()) {
                            String addr = findIpV4Address(ni);
                            if (addr != null) {
                                result = addr;
                            }
                            bestMatch = ni;
                            break;
                        } else {
                            bestMatch = ni;
                        }
                    }
                }
            }
        }
        if (bestMatch != null && "localhost".equals(result)) {
            String check = findIpV4Address(bestMatch);
            if (check != null) {
                return check;
            }
        }
        System.err.println("FOUND HOST NAME " + result);
        return result;
    }

    public int minimimLoggableLevel() {
        return minLevel;
    }
}
