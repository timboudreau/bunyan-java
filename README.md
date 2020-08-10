bunyan-java (original - obsoleted by bunyan-java-v2)
=========================================

__This project has been replaced with [bunyan-java-v2 which simplifies its API](https://github.com/timboudreau/bunyan-java-v2) which should be used instead__

An opinionated implementation of a simple logger in Java whose output is compatible with
[bunyan](https://github.com/trentm/node-bunyan), the excellent JSON
logger for [NodeJS](http://nodejs.org).

Uses [Guice](https://code.google.com/p/google-guice/) for injection, and has a fluent interface
and support for using ``@Named`` to inject named loggers.  Uses 
[AutoCloseable](http://timboudreau.com/blog/AutoCloseable/read) to enable a log record to
be appended to, builder-style, and automatically written when its ``close()`` method is
implicitly or explicitly called.


        class LogUser {
           private final Logger logger;
           LogUser(@Named("whatzit") Logger logger) {
              try (Log<Debug> log = logger.debug("someTask")) {
                  log.add("append to the message");
                  log.add(someObject);
                  log.add("foo", "bar");
              }
           }
        }

File or console logging or both are available, others can be implemented.


Dependencies
------------

Guice, Giulius, Jackson

You do not have to use Giulius, but the default ``LogWriter`` implementation expects to find
an instance of ``Settings`` bound.  If you want to use plain Guice, you'll need to implement
``LogWriter`` or provide a 
[Settings](http://timboudreau.com/builds/job/mastfrog-parent/lastSuccessfulBuild/artifact/giulius-modules/giulius-parent/giulius-settings/target/apidocs/com/mastfrog/settings/Settings.html).

It is also useful to include giulius-jackson's ``JacksonModule``, which will automatically
find and bind a Jackson module that adds an improved serializer for ``Throwable``s.

Integration With Logging Frameworks
-----------------------------------

Most logging frameworks have a concept of "appenders" or "handlers" which write output to some
destination.  Several subprojects here can be used to install bunyan-java as a logging destination.

 * bunyan-java-log4j-appender - an appender for Log4J 1.x
 * bunyan-java-util-logging - a Handler implementation for the JDK's logging library
 * bunyan-log4j2-appender - an appender for Log4J 2.x - pass `-Dlog4j.configurationFactory=com.mastfrog.bunyan.log4j2.appender.BunyanConfigurationFactory` to use

Each of these projects contains a Guice module which connects the logger to bunyan-java.  If you
use these adapters _you must also install the corresponding Guice module_ - logging prior to bunyan-java
being initialized is buffered and output to bunyan-java on initialization; if bunyan-java never gets initialized,
all logging will be buffered in-memory until there is no more memory.  For each framework, the
module optionally (constructor argument) will _attempt_ to reinitialize the logging framework to
use bunyan-java with no configuration files needed.  This is great to get the basics working, but to
ensure *all* logging goes through bunyan-java, configure your logging framework of choice appropriately
(usually system properties or files on disk or some combination thereof).

Note that `java.util.logging` and Log4J are _text-oriented_ as most traditional Java logging is,
meaning that their output is much less rich than you can get from using bunyan-java directly - but
these adapters make it possible to unify all logging to output through bunyan.

Note you will still need to use `LoggingModule` to set up bunyan-java, in addition to adapter modules.

Differences from Traditional Loggers
------------------------------------

### Designed for Dependency Injection

 * Bunyan-java is designed with dependency injection in mind, and utilizes Guice internally (you don't have to) - you can, for example,
during setup call `loggingModule.bindLogger("request")`, and then anything that needs an instance of `Logger` can just ask for
`@Named("request") Logger logger`

 * Log objects, not text - most logging information is processed by machines anyway - single lines of JSON make far more sense as an output format
than lines of plain text

 * A log level is an object - and it's a factory for `Log` instances

 * A `Log` instance represents one Log record you are going to write, and calling `close()` on it writes it - using the JDK's `AutoCloseable` to ensure it is:

```
try (Log<Debug> log = loggers.debug("saveFile")) {
   File newFile = findUnusedFile();
   log.add("to", newFile);
   // do some complicated logic here, and add different properties to the log record depending what happens
}
```

Usage
-----

Logging is configured using [Giulius](https://github.com/timboudreau/giulius) settings
API, which by default uses properties files which may be in ``/etc/``, ``~/`` and/or
``./``.  If you do not bind your own implementation of ``LogWriter``, the default 
one is affected by these properties:

 * ``log.file`` - if set to a file path, log to this file instead of the console
 * ``log.console`` - also log to the console, even if a file is set
 * ``log.async`` - write log records out on a background thread, so the caller is not blocked
   * A VM shutdown hook ensures that if the VM is not brutally killed, all pending log records are
flushed synchronously during shutdown
 * ``log.level`` - the level of log below which log records should be discarded

A Guice module, ``LoggingModule`` is provided, which makes it easy to use Guice's ``@Named``
to inject loggers:

		Settings s = new SettingsBuilder("app-name").addDefaultLocations().build();
                LoggingModule mod = new LoggingModule().bindLogger("mailer");

		Dependencies deps = Dependencies.builder().add(Namespace.DEFAULT, s).add(new LoggingModule());
                
		// ... then

		class Mailer {
                   Mailer (@Named("mailer") Logger logger, ...) { ...

Note: ``log.file`` console logging off uses buffered NIO - log records will be buffered, so logging may appear to stutter (a shutdown
hook will ensure any buffered records are written) - and can write two million records in about 10 seconds on a laptop with an SSD.
`log.async` is useful for performance if you will log to both console and a file, but uses a different file logging implementation.

[This unit test](https://github.com/timboudreau/bunyan-java/blob/master/src/test/java/com/mastfrog/bunyan/LoggerTest.java) shows
some usage.  It results in output which, indeed, can be parsed nicely with Bunyan:

![alt text](screen.png "Bunyan parsing a log generated with this library")


Classes
-------

We're aiming for simplicity more than flexibility here.  The following classes may be useful:

 * ``Loggers`` - A thing you get a logger from, injected by Guice, e.g. ``Logger logger = loggers.logger("whatever");``;
contains final fields for all known log levels
 * ``Logger`` - A thing which can create a ``Log`` tied to a particular level
 * ``Log`` - A thing which you add objects and key-value pairs to, which becomes one line of logging - one log record.
It implements an exception-free variant of the JDK's ``AutoClosable`` - the natural way to use it is within a 
*try-with-resources* block as shown above;  at the close of that block, the log record is written out

If you want to ship log records someplace special, you can implement and bind ``LogWriter``, which has one method,
``write(String)``.

### Log Levels are Objects

You may notice that log levels are actual objects - not constants.  So when you get a debug logger, you get a 
``Log<Debug>``, and so forth.

You can actually also start from a log level to create a ``Log``, e.g.

		@Inject
		MyThing(Loggers loggers) {
			try (Log<Debug> log = loggers.DEBUG.logger("stuff")) {
				log.add("This is the message");
				log.add("key", "value");
			} // log gets written out here!
		}


But What About Static Logging?
------------------------------

You don't need it.  I said this library was opinionated, right?


MongoDB Log Sink
----------------

The experimental subproject `bunyan-java-mongodb-sink` provides an implementation of bunyan-java's `LogSink` which
will route all logging to MongoDB.  This can be used in the following ways:

 * To route all logging to MongoDB, period, bind `LogSink` to `MongoDBLogSink` (there will be no console or other logging, no matter what you configured bunyan-java to do)
 * To use existing logging configuration, *and* MongoDB, bind `ComposableMongoDBSink` as an eager singleton, and call `LoggingModule.bindMultiLogSink()` when setting up your logging


