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
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.headers.Headers;
import com.mastfrog.acteur.util.RequestID;
import com.mastfrog.jackson.JacksonConfigurer;
import com.mastfrog.url.Path;
import com.mastfrog.url.URL;
import com.mastfrog.util.strings.Strings;
import com.mastfrog.util.time.TimeUtil;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.regex.Pattern;

public class BunyanJacksonConfig implements JacksonConfigurer {

    static final InetSocketAddressSerializer inetSer = new InetSocketAddressSerializer();
    static final SocketAddressSerializer socketSer = new SocketAddressSerializer();
    static final HttpEventSerializer HTTP = new HttpEventSerializer();


    @Override
    public ObjectMapper configure(ObjectMapper mapper) {
        SimpleModule sm = new SimpleModule("bunyan", new Version(1, 0, 1, null, "com.mastfrog", "bunyan-java"));
        // For logging purposes, iso dates are more useful
        sm.addSerializer(new DateTimeSerializer());
        sm.addSerializer(inetSer);
        sm.addSerializer(socketSer);
        sm.addSerializer(HTTP);
        sm.addSerializer(new EventSerializer());
        sm.addSerializer(new RequestIDSerializer());
        sm.addSerializer(new PathSerializer());
        sm.addSerializer(new UrlSerializer());
        sm.addSerializer(new ResponseStatusSerializer());
        sm.addDeserializer(ZonedDateTime.class, new DateTimeDeserializer());
        sm.addSerializer(new RequestIDSerializer());
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
            jg.writeString(TimeUtil.toIsoFormat(t));
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
            return TimeUtil.fromIsoFormat(string);
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

    @SuppressWarnings("unchecked")
    private static final class EventSerializer extends JsonSerializer<Event> {

        public Class<Event> handledType() {
            return Event.class;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void serialize(Event t, JsonGenerator jg, SerializerProvider sp) throws IOException, JsonProcessingException {
            if (t instanceof HttpEvent) {
                HTTP.serialize((HttpEvent) t, jg, sp);
            } else {
                jg.writeStartObject();
                jg.writeStringField("type", t.getClass().getSimpleName());
                SocketAddress addr = t.remoteAddress();
                jg.writeFieldName("address");
                if (addr instanceof InetSocketAddress) {
                    inetSer.serialize((InetSocketAddress) addr, jg, sp);
                } else {
                    socketSer.serialize(addr, jg, sp);
                }
                jg.writeNumberField("len", t.content().readableBytes());
                if (t.request() instanceof WebSocketFrame) {
                    WebSocketFrame wsf = (WebSocketFrame) t.request();
                    jg.writeBooleanField("ff", wsf.isFinalFragment());
                }
                jg.writeEndObject();
            }
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
            jg.writeString(t.path().toString());
            jg.writeFieldName("address");
            SocketAddress addr = t.remoteAddress();
            if (addr instanceof InetSocketAddress) {
                inetSer.serialize((InetSocketAddress) addr, jg, sp);
            } else {
                socketSer.serialize(addr, jg, sp);
            }
            jg.writeFieldName("method");
            jg.writeString(t.method().name());

            if (Strings.contains('?', t.request().uri())) {
                jg.writeFieldName("params");
                jg.writeStartObject();
                for (Map.Entry<String, String> e : t.urlParametersAsMap().entrySet()) {
                    jg.writeFieldName(e.getKey());
                    jg.writeString(e.getValue());
                }
                jg.writeEndObject();
            }
            if (t.header(Headers.REFERRER) != null) {
                jg.writeFieldName("referrer");
                jg.writeString(t.header(Headers.REFERRER).toString());
            }
            if (t.header(Headers.HOST) != null) {
                jg.writeStringField("host", t.header(Headers.HOST).toString());
            }
            if (t.header(Headers.USER_AGENT) != null) {
                jg.writeFieldName("agent");
                jg.writeString(t.header(Headers.USER_AGENT) + "");
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
