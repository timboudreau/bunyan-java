/*
 * The MIT License
 *
 * Copyright 2017 Tim Boudreau.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.mastfrog.bunyan.parse;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * A filter predicate for LogStreams. You can use a plain predicate, but this
 * class allows for better performance by, where possible, filtering on the
 * string value of each log line before it is converted to an object, to avoid
 * doing extra marshalling work for lines that will be thrown away.
 *
 * @author Tim Boudreau
 */
public abstract class LogFilter implements Predicate<LogRecord> {

    /**
     * Add to the passed collection of strings any strings which, if
     * present, mean a particular log line should <b>not</b> be ignored
     * (careful!).
     * @param strings 
     */
    protected void collectPrefilterStrings(Set<? super String> strings) {
        //do nothing
    }

    final Predicate<String> prefilterPredicate() {
        Predicate<String> result = new JsonHashCheck();
        Set<String> toMatch = new HashSet<>();
        collectPrefilterStrings(toMatch);
        if (toMatch.isEmpty()) {
            return result;
        }
        String[] strings = toMatch.toArray(new String[toMatch.size()]);
        // Fasiter fail if shortest strings are tested first
        Arrays.sort(strings, (String t, String t1) -> {
            Integer a = t.length();
            Integer b = t1.length();
            return a.compareTo(b);
        });
        return result.and(new MatchWords(true, strings));
    }
    
    public static LogFilter levelMatches(int level) {
        return new LogFilter() {
            @Override
            public boolean test(LogRecord t) {
                return t.level == level;
            }
        };
    }

    public static LogFilter levelGreaterThan(int level) {
        return new LogFilter() {
            @Override
            public boolean test(LogRecord t) {
                return t.level > level;
            }
        };
    }

    public static LogFilter levelLessThan(int level) {
        return new LogFilter() {
            @Override
            public boolean test(LogRecord t) {
                return t.level < level;
            }
        };
    }

    public static LogFilter before(ZonedDateTime dt) {
        if (dt == null) {
            throw new IllegalArgumentException("Null date");
        }
        return new LogFilter() {
            @Override
            public boolean test(LogRecord t) {
                return t.time.isBefore(dt);
            }
        };
    }

    public static LogFilter after(ZonedDateTime dt) {
        if (dt == null) {
            throw new IllegalArgumentException("Null date");
        }
        return new LogFilter() {
            @Override
            public boolean test(LogRecord t) {
                return t.time.isAfter(dt);
            }
        };
    }

    public static LogFilter named(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Null name");
        }
        LogFilter result = new LogFilter() {
            @Override
            public boolean test(LogRecord t) {
                return name.equals(t.name);
            }

            @Override
            protected void collectPrefilterStrings(Set<? super String> strings) {
                strings.add(name);
            }

        };
        return result;
    }

    public static LogFilter skip(long count) {
        if (count < 0) {
            throw new IllegalArgumentException("Negative skip");
        }
        LogFilter result = new LogFilter() {
            AtomicLong counter = new AtomicLong();

            @Override
            public boolean test(LogRecord t) {
                return counter.getAndIncrement() >= count;
            }

        };
        return result;
    }    
    public static LogFilter limit(long limit) {
        if (limit < 0) {
            throw new IllegalArgumentException("Negative limit");
        }
        LogFilter result = new LogFilter() {
            AtomicLong counter = new AtomicLong();

            @Override
            public boolean test(LogRecord t) {
                return counter.getAndIncrement() < limit;
            }

        };
        return result;
    }

    public static LogFilter ignore(int every) {
        if (every <= 0) {
            throw new IllegalArgumentException("Cannot divide by zero or skip"
                    + " a negative count");
        }
        LogFilter result = new LogFilter() {
            AtomicInteger counter = new AtomicInteger();
            @Override
            public boolean test(LogRecord t) {
                return counter.getAndIncrement() % every == 0;
            }
        };
        return result;
    }

    public static LogFilter propertyEquals(String name, Object value) {
        LogFilter result = new LogFilter() {
            @Override
            public boolean test(LogRecord t) {
                Object val = t.props.get(name);
                return value.equals(val);
            }

            @Override
            protected void collectPrefilterStrings(Set<? super String> strings) {
                strings.add(name);
                if (value instanceof String) {
                    strings.add((String) value);
                }
            }
        };
        return result;
    }

    public static LogFilter propertyMatches(String name, Pattern value) {
        LogFilter result = new LogFilter() {
            @Override
            public boolean test(LogRecord t) {
                Object val = t.props.get(name);
                return val instanceof String && value.matcher(val.toString()).matches();
            }

            @Override
            protected void collectPrefilterStrings(Set<? super String> strings) {
                strings.add(name);
            }
        };
        return result;
    }

    public static LogFilter message(String msg) {
        return new LogFilter() {
            @Override
            public boolean test(LogRecord t) {
                return msg.equals(t.msg);
            }
        };
    }

    public final LogFilter or(LogFilter filter) {
        return new Or(this, filter);
    }

    public final LogFilter and(LogFilter filter) {
        return new And(this, filter);
    }

    public final LogFilter negate() {
        return new Negate(this);
    }

    private static final class Negate extends LogFilter {

        private final LogFilter filter;

        Negate(LogFilter filter) {
            this.filter = filter;
        }

        @Override
        public boolean test(LogRecord t) {
            return !filter.test(t);
        }

        @Override
        protected void collectPrefilterStrings(Set<? super String> strings) {
            // do nothing
        }
    }

    private static final class Or extends LogFilter {

        private final LogFilter a;
        private final LogFilter b;

        public Or(LogFilter a, LogFilter b) {
            this.a = a;
            this.b = b;
        }

        @Override
        public boolean test(LogRecord t) {
            return a.test(t) || b.test(t);
        }

        @Override
        protected void collectPrefilterStrings(Set<? super String> strings) {
            a.collectPrefilterStrings(strings);
            b.collectPrefilterStrings(strings);
        }
    }

    private static final class And extends LogFilter {

        private final LogFilter a;
        private final LogFilter b;

        public And(LogFilter a, LogFilter b) {
            this.a = a;
            this.b = b;
        }

        @Override
        public boolean test(LogRecord t) {
            return a.test(t) && b.test(t);
        }

        @Override
        protected void collectPrefilterStrings(Set<? super String> strings) {
            a.collectPrefilterStrings(strings);
            b.collectPrefilterStrings(strings);
        }
    }
}
