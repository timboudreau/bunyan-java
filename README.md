bunyan-java
===========

An opinionated implementation of a simple logger in Java whose output is compatible with
[bunyan](https://github.com/trentm/node-bunyan) JSON logger, the excellent 
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

Joda Time, Guice, Giulius, Jackson
