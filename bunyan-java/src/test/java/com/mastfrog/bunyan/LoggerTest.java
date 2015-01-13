/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.mastfrog.bunyan;

import com.mastfrog.bunyan.LoggerTest.LM;
import com.mastfrog.bunyan.type.Warn;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.TestWith;
import com.mastfrog.jackson.JacksonModule;
import com.mastfrog.util.collections.MapBuilder;
import java.util.Map;
import java.util.Random;
import javax.inject.Inject;
import javax.inject.Named;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author Tim Boudreau
 */
@RunWith(GuiceRunner.class)
@TestWith({JacksonModule.class, LM.class})
public class LoggerTest {

    @Test
    public void test(Loggers loggers, Thing thing) throws InterruptedException {
        try (Log<Warn> log = thing.logger.warn("wunk").message("hoobie")) {
            log.add("foo", "bar");
            log.add("baz", 23);
            Bean b = new Bean();
            b.hoogie = "wuz";
            b.isTrue = true;
            log.add("bean", b);
        }
        Random r = new Random(System.currentTimeMillis());
        for (int i = 0; i < 150; i++) {
            try (Log<?> log = loggers.log(loggers.error, "foodbar").message("whatzit")) {
                log.add("ran", r.nextInt());
                log.add("ix", i);
            };
        }
        thing.logger.debug(new IllegalStateException("Hoobie")).message("A bad thing happened").close();
    }

    private static class Thing {

        private final Logger logger;

        @Inject
        public Thing(@Named("whoozie") Logger logger) {
            this.logger = logger;
        }
    }

    public static final class Bean {

        public String hoogie;
        public boolean isTrue;
        public Map<String, Object> nested = new MapBuilder().put("hey", "you").build();
    }

    static class LM extends LoggingModule {

        LM() {
            super.bindLogger("whoozie");
        }
    }
}
