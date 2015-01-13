package com.mastfrog.bunyan;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.mastfrog.jackson.JacksonConfigurer;
import java.io.IOException;
import org.openide.util.lookup.ServiceProvider;

/**
 * If you install Jackson-Guice's JacksonModule, this will bind better logging
 * of Throwables.
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service=JacksonConfigurer.class)
public class JacksonConfig implements JacksonConfigurer {

    @Override
    public ObjectMapper configure(ObjectMapper om) {
        SimpleModule sm = new SimpleModule("mongo", new Version(1, 0, 0, null, "com.visitrend", "userserver"));
        sm.addSerializer(new ThrowableSerializer());
        om.registerModule(sm);
        return om;
    }

    static class ThrowableSerializer extends JsonSerializer<Throwable> {

        public ThrowableSerializer() {
        }

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
                jg.writeStartObject();
                serialize(t.getCause(), jg, sp);
            }
            jg.writeEndObject();
        }

    }

}
