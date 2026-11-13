package org.konaframework.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.konaframework.support.collection.Collection;
import org.konaframework.support.collection.LazyCollection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class JavaLazyCollectionTest {
    public static class Row {
        private final String name;
        private final boolean active;

        public Row(String name, boolean active) {
            this.name = name;
            this.active = active;
        }

        public String getName() {
            return name;
        }

        public boolean isActive() {
            return active;
        }

        public void ping() {
            pings += 1;
        }

        public static int pings;
    }

    @Test
    void lazyAndHigherOrderFromJava() {
        assertEquals(List.of(2), LazyCollection.make(1, 2, 3).filter(n -> n % 2 == 0).collect().toList());
        assertTrue(LazyCollection.of(List.of()).isEmpty());
        assertTrue(LazyCollection.empty().isEmpty());
        assertEquals(List.of(1), new LazyCollection<>(List.of(1)).all());

        Collection<Row> rows = Collection.make(new Row("Ada", true), new Row("Bea", false));
        Collection<?> names = (Collection<?>) rows.higher("map").get("name");
        assertEquals(List.of("Ada", "Bea"), names.toList());
        Collection<?> active = (Collection<?>) rows.higher("filter").get("active");
        assertEquals(1, active.count());
        Row.pings = 0;
        rows.higher("each").call("ping");
        assertEquals(2, Row.pings);
        assertEquals(0.0, Collection.make(Map.of("votes", 0)).higher("sum").get("votes"));
        assertNull(Collection.make(Map.of("name", "Ada")).lazy().higher("first").get("missing"));
    }
}
