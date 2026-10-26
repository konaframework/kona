package org.konaframework.crema;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Java objects are readable from Crema after a null check. */
class CremaAccessJavaTest {
    public static class Person {
        public String getEmail() {
            return "a@b.c";
        }
    }

    public static class Row {
        public Object getAttribute(String key) {
            return "ada";
        }
    }

    @Test
    void nullCheckAndAttribute() {
        CremaEngine crema = new CremaEngine(new MapViewFinder());
        String checked = "@if(user !== null){{ user.getEmail() }}@else none@endif";
        assertEquals("a@b.c", crema.renderSource(checked, Map.of("user", new Person())));
        Map<String, Object> missing = new HashMap<>();
        missing.put("user", null);
        assertEquals(" none", crema.renderSource(checked, missing));
        assertEquals("ada", crema.renderSource("{{ row.name }}", Map.of("row", new Row())));
    }
}
