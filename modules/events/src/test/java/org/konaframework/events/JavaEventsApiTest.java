package org.konaframework.events;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class JavaEventsApiTest {
    public static final class Ping {
        public final String value;

        public Ping(String value) {
            this.value = value;
        }
    }

    @Test
    void samListenersFromJava() {
        EventDispatcher bus = new EventDispatcher();
        List<String> seen = new ArrayList<>();
        bus.listen(Ping.class, event -> seen.add(event.value));
        bus.listen(Ping.class, (EventListener<Ping>) event -> seen.add(event.value + "!"));
        bus.dispatch(new Ping("hi"));
        assertEquals(List.of("hi", "hi!"), seen);
    }
}
