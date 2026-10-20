package org.konaframework.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.konaframework.core.Application;
import org.konaframework.support.collection.Collection;
import org.konaframework.support.date.KonaDate;
import org.konaframework.support.helpers.Arr;
import org.konaframework.support.lang.Lang;
import org.konaframework.support.lang.Translator;
import org.konaframework.support.str.Str;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class JavaSupportApiTest {
    @Test
    void collectionStrDateArrLangFromJava(@TempDir Path base) {
        Collection<Integer> nums = Collection.make(3, 1, 2);
        assertEquals(3, nums.count());
        assertEquals(List.of(1, 2, 3), nums.sort().toList());
        assertEquals(Collection.of(List.of(1)), Collection.empty().push(1));

        assertEquals("helloWorld", Str.camel("hello_world"));
        assertEquals("hello-world", Str.slug("Hello World"));
        assertEquals("hello_world", Str.slug("Hello World", "_"));
        assertEquals("HELLO", Str.of("hello").upper().toString());

        KonaDate date = KonaDate.ofInstant(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC);
        assertEquals("2024-01-01", date.toDateString());
        assertTrue(date.addDays(1).isAfter(date));

        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        Arr.set(map, "user.name", "Ada");
        assertEquals("Ada", Arr.get(map, "user.name"));
        assertTrue(Arr.has(map, "user.name"));
        assertEquals(Map.of("user.name", "Ada"), Arr.only(Map.of("user.name", "Ada", "x", 1), List.of("user.name")));

        Application app = new Application(base);
        app.register(new SupportServiceProvider());
        Translator translator = app.resolve(Translator.class);
        translator.addLines("en", Map.of("greet", "Hi :name"));
        assertEquals("Hi Ada", Lang.get("greet", Map.of("name", "Ada")));
        Lang.setLocale("en");
        assertEquals("en", Lang.getLocale());
        assertFalse(Str.isBlank("x"));

        Translator oneArg = new Translator("en");
        oneArg.addLines("en", Map.of("hi", "Hello :name"));
        assertEquals("Hello Ada", oneArg.get("hi", Map.of("name", "Ada")));
        LinkedHashMap<String, Object> withNull = new LinkedHashMap<>();
        withNull.put("name", null);
        assertEquals("Hello ", oneArg.get("hi", withNull));
        assertEquals("Hello Ada", oneArg.trans("hi", Map.of("name", "Ada")));
        KonaDate.now();
        KonaDate.now(ZoneOffset.UTC);
        KonaDate.today();
        KonaDate.today(ZoneOffset.UTC);
        KonaDate.setDefaultTimezone(ZoneOffset.UTC);
        assertEquals(ZoneOffset.UTC, KonaDate.defaultTimezone());
        KonaDate.resetDefaultTimezone();
        Lang.setFallback("en");
        assertEquals("en", Lang.getFallback());

        Collection<Integer> mutable = Collection.make(1);
        mutable.put(0, 7);
        mutable.put(99, 8);
        assertEquals(7, mutable.first());
        assertEquals(8, mutable.last());
        assertEquals("", Str.studly("___"));
        assertEquals("", Str.camel(""));
        assertEquals("Hello World", Str.title("hello   world"));
        Translator twoArg = new Translator("fr", "en");
        assertEquals("fr", twoArg.getLocale());
    }
}
