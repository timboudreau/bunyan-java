/*
 * The MIT License
 *
 * Copyright 2015 Tim Boudreau.
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
package com.mastfrog.bunyan;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.mastfrog.jackson.JacksonConfigurer;
import com.mastfrog.util.service.ServiceProvider;
import java.io.IOException;
import javax.inject.Singleton;

/**
 * If you install Jackson-Guice's JacksonModule, this will bind better logging
 * of Throwables.
 *
 * @author Tim Boudreau
 */
@ServiceProvider(JacksonConfigurer.class)
@Singleton
public class JacksonConfig implements JacksonConfigurer {

    @Override
    public ObjectMapper configure(ObjectMapper om) {
        SimpleModule sm = new SimpleModule("mongo", new Version(1, 0, 0, null, "com.mastfrog", "throwables"));
        sm.addSerializer(new ThrowableSerializer());
        om.registerModule(sm);
        return om;
    }

    static class ThrowableSerializer extends JsonSerializer<Throwable> {

        @Override
        public Class<Throwable> handledType() {
            return Throwable.class;
        }

        @Override
        public void serialize(Throwable t, JsonGenerator jg, SerializerProvider sp) throws IOException, JsonProcessingException {
            jg.writeStartObject();
            jg.writeFieldName("type");
            jg.writeString(t.getClass().getName());
            if (t.getMessage() != null) {
                jg.writeFieldName("msg");
                jg.writeString(t.getMessage());
            } else {
                jg.writeOmittedField("msg");
            }
            jg.writeArrayFieldStart("stack");
            StackTraceElement[] ste = t.getStackTrace();
            if (ste != null) {
                for (StackTraceElement e : ste) {
                    jg.writeStartObject();
                    jg.writeFieldName("class");
                    jg.writeString(e.getClassName());
                    jg.writeFieldName("method");
                    jg.writeString(e.getMethodName());
                    jg.writeFieldName("line");
                    jg.writeNumber(e.getLineNumber());
                    jg.writeFieldName("src");
                    jg.writeString(e.getFileName());
                    jg.writeEndObject();
                }
            }
            jg.writeEndArray();
            if (t.getCause() != null) {
                jg.writeFieldName("cause");
                serialize(t.getCause(), jg, sp);
            }
            Throwable[] causes = t.getSuppressed();
            if (causes != null && causes.length > 0) {
                jg.writeFieldName("suppressed");
                jg.writeStartArray();
                try {
                    for (Throwable tt : causes) {
                        serialize(tt, jg, sp);
                    }
                } finally {
                    jg.writeEndArray();
                }
            }
            jg.writeEndObject();
        }
    }
}
