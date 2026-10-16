package io.konaframework.support;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.konaframework.support.collection.Collection;
import io.konaframework.support.lang.Translator;
import io.konaframework.support.str.Str;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class JavaTranslatorOverloadsTest {
    @Test
    void translatorGetAndCollectionPutBranches() {
        Translator oneArg = new Translator("en");
        oneArg.addLines("en", Map.of("a", "A :x"));
        assertEquals("A 1", oneArg.get("a", Map.of("x", 1)));
        assertEquals("A 2", oneArg.trans("a", Map.of("x", 2)));
        assertEquals("A :x", oneArg.trans("a"));
        assertEquals("A ", oneArg.get("a", Map.of("x", "")));

        Collection<String> c = Collection.make("a");
        c.put(0, "b");
        assertEquals("b", c.get(0));
        c.put(-1, "c");
        assertEquals("c", c.last());
        c.put(100, "d");
        assertEquals("d", c.last());

        assertEquals("Hello", Str.title(" hello "));
        assertEquals("", Str.title(""));
        assertEquals("A", Str.title("a"));
        assertEquals("", Str.camel(""));
        assertEquals("a", Str.camel("A"));
        assertEquals("HelloWorld", Str.studly("__hello__world__"));
        assertEquals("hello_world", Str.snake("HelloWorld"));
        assertEquals("hello_world", Str.snake("__Hello__World__"));
    }
}

