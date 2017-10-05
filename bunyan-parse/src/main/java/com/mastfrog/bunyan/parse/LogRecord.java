/*
 * The MIT License
 *
 * Copyright 2017 tim.
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

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A bunyan-style log record, with fields for the required fields in a log
 * record, and an internal map for the rest.
 *
 * @author Tim Boudreau
 */
public class LogRecord implements Iterable<String> {

    public final String name;
    public final String msg;
    public final int pid;
    public final String hostName;
    public final ZonedDateTime time;
    public final int level;
    public @JsonIgnore
    final Map<String, Object> props = new LinkedHashMap<>();

    /**
     * Create a new log record.
     *
     * @param name The name property
     * @param msg The message property
     * @param time The time property
     * @param version The version property
     * @param level The log level property
     * @param pid The process-id property
     * @param hostName The host name property
     */
    public LogRecord(@JsonProperty("name") String name, @JsonProperty("msg") String msg,
            @JsonProperty("time") ZonedDateTime time, @JsonProperty("v") int version,
            @JsonProperty("level") int level,
            @JsonProperty("pid") int pid, @JsonProperty("hostname") String hostName) {
        this.name = name.intern();
        this.msg = msg.intern();
        this.pid = pid;
        this.hostName = hostName.intern();
        this.time = time;
        this.level = level;
    }

    /**
     * Convert this object to a Map.
     *
     * @return A map
     */
    @JsonIgnore
    public Map<String, Object> toMap() {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("name", name);
        result.put("msg", msg);
        result.put("time", time);
        result.put("level", level);
        result.put("pid", pid);
        result.put("hostname", hostName);
        result.putAll(props);
        return result;
    }

    /**
     * Get the names of properties available from the get() method which are
     * <i>not defined as fields of this class</i>.
     *
     * @return A set of property names
     */
    @JsonIgnore
    public Set<String> keys() {
        return Collections.unmodifiableSet(props.keySet());
    }

    @JsonAnySetter
    public void put(String name, Object val) {
        props.put(name, val);
    }

    /**
     * Get an ad-hoc property that is part of this log record.
     *
     * @param name The name of the property
     * @return An object or null
     */
    @JsonAnyGetter
    public Object get(String name) {
        return props.get(name);
    }

    /**
     * Convenience method to get an ad-hoc property cast to a specific type.
     * Does not do any type-coercion.
     *
     * @param <T> The type to convert to
     * @param name The property name
     * @param type The type to fetch the property as
     * @throws ClassCastException if the property is non-null but is not of a
     * type compatible with T
     * @return The property value cast to type T or null
     */
    @JsonIgnore
    public <T> T get(String name, Class<T> type) {
        Object o = get(name);
        return o == null ? null : type.cast(o);
    }

    /**
     * Get an iterator of the property names available from the get() methods.
     *
     * @return An iterator
     */
    @Override
    public Iterator<String> iterator() {
        return props.keySet().iterator();
    }

    @Override
    public String toString() {
        return "name=" + name + ", msg=" + msg + ", hostName=" + hostName + ", time=" + time + ", level=" + level + ", props=" + props + '}';
    }

    /**
     * Returns true if this record has an ad-hoc property of the passed name.
     *
     * @param name The name
     * @return True if present (even if null), false if absent
     */
    public boolean has(String name) {
        return props.containsKey(name);
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 23 * hash + Objects.hashCode(this.name);
        hash = 23 * hash + Objects.hashCode(this.msg);
        hash = 23 * hash + Objects.hashCode(this.hostName);
        hash = 23 * hash + Objects.hashCode(this.time);
        hash = 23 * hash + this.level;
        hash = 23 * hash + Objects.hashCode(this.props);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final LogRecord other = (LogRecord) obj;
        if (this.level != other.level) {
            return false;
        }
        if (!Objects.equals(this.name, other.name)) {
            return false;
        }
        if (!Objects.equals(this.msg, other.msg)) {
            return false;
        }
        if (!Objects.equals(this.hostName, other.hostName)) {
            return false;
        }
        if (!Objects.equals(this.time, other.time)) {
            return false;
        }
        if (!Objects.equals(this.props, other.props)) {
            return false;
        }
        return true;
    }
}
