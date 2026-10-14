package dev.kona.support;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.kona.support.lang.Lang;
import dev.kona.support.lang.Translations;
import dev.kona.support.lang.Translator;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class JavaTranslationsTest {
    @Test
    void topLevelUnderscoreHelpersAreCallable() {
        Translator translator = new Translator("en");
        translator.addLines("en", Map.of("hi", "Hello :name"));
        Lang.setTranslator(translator);

        assertEquals("Hello :name", Translations.__("hi"));
        assertEquals("Hello Ada", Translations.__("hi", Map.of("name", "Ada")));
    }
}
