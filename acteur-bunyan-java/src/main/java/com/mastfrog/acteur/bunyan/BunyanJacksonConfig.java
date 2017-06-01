package com.mastfrog.acteur.bunyan;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.headers.Headers;
import com.mastfrog.acteur.util.RequestID;
import com.mastfrog.jackson.JacksonConfigurer;
import com.mastfrog.url.Path;
import com.mastfrog.url.URL;
import com.mastfrog.util.time.TimeUtil;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.regex.Pattern;

public class BunyanJacksonConfig implements JacksonConfigurer {

    static final InetSocketAddressSerializer inetSer = new InetSocketAddressSerializer();
    static final SocketAddressSerializer socketSer = new SocketAddressSerializer();

    @Override
    public ObjectMapper configure(ObjectMapper mapper) {
        SimpleModule sm = new SimpleModule("bunyan", new Version(1, 0, 1, null, "com.mastfrog", "bunyan-java"));
        // For logging purposes, iso dates are more useful
        sm.addSerializer(new DateTimeSerializer());
        sm.addSerializer(inetSer);
        sm.addSerializer(socketSer);
        sm.addSerializer(new HttpEventSerializer());
        sm.addSerializer(new RequestIDSerializer());
        sm.addSerializer(new PathSerializer());
        sm.addSerializer(new UrlSerializer());
        sm.addSerializer(new ResponseStatusSerializer());
        sm.addDeserializer(ZonedDateTime.class, new DateTimeDeserializer());
        mapper.registerModule(sm);
        return mapper;
    }

    private static final class ResponseStatusSerializer extends JsonSerializer<HttpResponseStatus> {

        @Override
        public Class<HttpResponseStatus> handledType() {
            return HttpResponseStatus.class;
        }

        @Override
        public void serialize(HttpResponseStatus t, JsonGenerator jg, SerializerProvider sp) throws IOException, JsonProcessingException {
            jg.writeStartObject();
            jg.writeFieldName("reason");
            jg.writeString(t.reasonPhrase());
            jg.writeFieldName("code");
            jg.writeNumber(t.code());;
            jg.writeEndObject();
        }
    }

    private static final class PathSerializer extends JsonSerializer<Path> {

        @Override
        public Class<Path> handledType() {
            return Path.class;
        }

        @Override
        public void serialize(Path t, JsonGenerator jg, SerializerProvider sp) throws IOException, JsonProcessingException {
            jg.writeString(t.toString());
        }
    }

    private static final class UrlSerializer extends JsonSerializer<URL> {

        @Override
        public Class<URL> handledType() {
            return URL.class;
        }

        @Override
        public void serialize(URL t, JsonGenerator jg, SerializerProvider sp) throws IOException, JsonProcessingException {
            jg.writeString(t.toString());
        }
    }

    private static class DateTimeSerializer extends JsonSerializer<ZonedDateTime> {

        @Override
        public Class<ZonedDateTime> handledType() {
            return ZonedDateTime.class;
        }

        @Override
        public void serialize(ZonedDateTime t, JsonGenerator jg, SerializerProvider sp) throws IOException, JsonProcessingException {
            jg.writeString(Headers.ISO2822DateFormat.format(t));
        }
    }

    private static class DateTimeDeserializer extends JsonDeserializer<ZonedDateTime> {

        @Override
        public boolean isCachable() {
            return true;
        }

        @Override
        public Class<?> handledType() {
            return ZonedDateTime.class;
        }

        private static final Pattern NUMBERS = Pattern.compile("^\\d+$");

        @Override
        public ZonedDateTime deserialize(JsonParser jp, DeserializationContext dc) throws IOException, JsonProcessingException {
            String string = jp.readValueAs(String.class);
            if (NUMBERS.matcher(string).matches()) {
                return TimeUtil.fromUnixTimestamp(Long.parseLong(string));
            }
            return ZonedDateTime.parse(string, Headers.ISO2822DateFormat);
        }
    }

    private static final class SocketAddressSerializer extends JsonSerializer<SocketAddress> {

        @Override
        public Class<SocketAddress> handledType() {
            return SocketAddress.class;
        }

        @Override
        public void serialize(SocketAddress t, JsonGenerator jg, SerializerProvider sp) throws IOException, JsonProcessingException {
            String s = t.toString();
            if (s.startsWith("/")) {
                s = s.substring(1);
            }
            jg.writeString(s);
        }

    }

    private static final class InetSocketAddressSerializer extends JsonSerializer<InetSocketAddress> {

        @Override
        public Class<InetSocketAddress> handledType() {
            return InetSocketAddress.class;
        }

        @Override
        public void serialize(InetSocketAddress t, JsonGenerator jg, SerializerProvider sp) throws IOException, JsonProcessingException {
//            jg.writeStartObject();
//            jg.writeFieldName("address");
            jg.writeString(t.getHostString());
//            jg.writeFieldName("port");
//            jg.writeNumber(t.getPort());
//            jg.writeEndObject();
        }
    }

    private static final class HttpEventSerializer extends JsonSerializer<HttpEvent> {

        @Override
        public Class<HttpEvent> handledType() {
            return HttpEvent.class;
        }

        @Override
        public void serialize(HttpEvent t, JsonGenerator jg, SerializerProvider sp) throws IOException, JsonProcessingException {
            jg.writeStartObject();
            jg.writeFieldName("path");
            jg.writeString(t.getPath().toString());
            jg.writeFieldName("address");
            SocketAddress addr = t.getRemoteAddress();
            if (addr instanceof InetSocketAddress) {
                inetSer.serialize((InetSocketAddress) addr, jg, sp);
            } else {
                socketSer.serialize(addr, jg, sp);
            }
            jg.writeFieldName("method");
            jg.writeString(t.getMethod().name());
            jg.writeFieldName("params");
            jg.writeStartObject();
            for (Map.Entry<String, String> e : t.getParametersAsMap().entrySet()) {
                jg.writeFieldName(e.getKey());
                jg.writeString(e.getValue());
            }
            jg.writeEndObject();
            if (t.getHeader(Headers.REFERRER) != null) {
                jg.writeFieldName("referrer");
                jg.writeString(t.getHeader(Headers.REFERRER) + "");
            }
            if (t.getHeader(Headers.USER_AGENT) != null) {
                jg.writeFieldName("agent");
                jg.writeString(t.getHeader(Headers.USER_AGENT) + "");
            }
            jg.writeEndObject();
        }
    }

    private static class RequestIDSerializer extends JsonSerializer<RequestID> {

        @Override
        public Class<RequestID> handledType() {
            return RequestID.class;
        }

        @Override
        public void serialize(RequestID t, JsonGenerator jg, SerializerProvider sp) throws IOException, JsonProcessingException {
            jg.writeString(t.stringValue());
        }
    }
}
