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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.mastfrog.bunyan.LoggingModule;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.settings.SettingsBuilder;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 *
 * @author Tim Boudreau
 */
public class TestIt {

    static final class M implements Module {
        @Override
        public void configure(Binder binder) {
            binder.bind(Path.class).toInstance(Paths.get(new File("/tmp/mf-old.log").toURI()));
        }
    }

    public static void main(String[] args) throws IOException {
        LoggingModule lm = new LoggingModule(false).withConfigurer((ObjectMapper om) -> om.registerModule(new JodaModule()));
        Dependencies deps = new Dependencies(SettingsBuilder.createDefault().build(), lm, new M());
        LogStreamFactory stream = deps.getInstance(LogStreamFactory.class);
        
        stream.stream(LogFilter.named("main").and(LogFilter.propertyEquals("src", "Tex Stylucwellinson"))).forEachOrdered((t) -> {
            System.out.println(t.get("recs"));
        });
        
        stream.read(LogFilter.named("ratings"), (t) -> {
            System.out.println(t);
            Map<String,Object> recs = (Map<String,Object>) t.get("recs");
            for (Map.Entry<String, Object> e : recs.entrySet()) {
                System.out.println(e.getKey() + "\t" + e.getValue());
            }
            return ct++ < 100 ? LogStreamFactory.ReadResult.CONTINUE : LogStreamFactory.ReadResult.STOP;
        });
    }

    static int ct = 0;
}
