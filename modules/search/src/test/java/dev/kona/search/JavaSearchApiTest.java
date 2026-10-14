package dev.kona.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.kona.core.Application;
import java.nio.file.Files;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class JavaSearchApiTest {
    static final class Article implements Searchable {
        private final String id;
        private final String title;

        Article(String id, String title) {
            this.id = id;
            this.title = title;
        }

        @Override
        public String searchableAs() {
            return "articles";
        }

        @Override
        public String getScoutKey() {
            return id;
        }

        @Override
        public Map<String, Object> toSearchableArray() {
            return Map.of("title", title);
        }
    }

    @Test
    void searchFromJava() throws Exception {
        var base = Files.createTempDirectory("kona-search");
        Application app = new Application(base);
        app.register(new SearchServiceProvider()).boot();
        Scout.update(new Article("1", "Hello Kona"));
        assertEquals(1, Scout.search("articles", "kona").keys().size());
        assertTrue(Scout.manager().collection().size("articles") == 1);
    }
}
