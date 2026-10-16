package io.konaframework.cadence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JavaCadenceB6Test {
    @BeforeEach
    void setUp() {
        TestDb.INSTANCE.create();
        Database.global().withConnection(connection -> {
            var schema = new io.konaframework.cadence.schema.Schema(connection);
            schema.create("authors", bp -> {
                bp.id();
                bp.string("name");
                bp.string("display_name").nullable();
            });
            schema.create("books", bp -> {
                bp.id();
                bp.foreignId("author_id");
                bp.string("title");
                bp.integer("votes");
                bp.timestamps();
            });
            return null;
        });
        B6Author.Companion.create(Map.of("name", "Ada", "display_name", "ada"));
        B6Book.Companion.create(Map.of("title", "A1", "author_id", 1L, "votes", 4));
        B6Book.Companion.create(Map.of("title", "A2", "author_id", 1L, "votes", 6));
    }

    @AfterEach
    void tearDown() {
        Cadence.reguard();
        Database.clearGlobal();
    }

    @Test
    void aggregatesSoleAndStaticHelpersFromJava() {
        var avg = B6Author.Companion.withAvg("books", "votes").where("name", "Ada").first();
        assertEquals(5.0, ((Number) avg.getRawAttribute("books_avg_votes")).doubleValue(), 0.001);
        assertEquals(6, ((Number) B6Author.Companion.withMax("books", "votes").where("name", "Ada").first()
                .getRawAttribute("books_max_votes")).intValue());
        assertEquals(4, ((Number) B6Author.Companion.withMin("books:votes").where("name", "Ada").first()
                .getRawAttribute("books_min_votes")).intValue());
        assertEquals("Ada", B6Author.Companion.where("name", "Ada").sole().get("name"));

        Cadence.unguard();
        assertTrue(Cadence.isUnguarded());
        B6Author created = Cadence.unguarded((Supplier<B6Author>) () -> {
            B6Author a = new B6Author();
            a.forceFill(Map.of("name", "Java"));
            a.save();
            return a;
        });
        assertTrue(created.exists());
        Cadence.reguard();
        assertFalse(Cadence.isUnguarded());

        Cadence.withoutTimestamps((Supplier<Boolean>) () -> {
            B6Author a = B6Author.Companion.where("name", "Ada").first();
            a.setAttribute("name", "Ada2");
            return a.save();
        });
        assertFalse(Cadence.isIgnoringTimestamps());
        assertEquals("Ada2", B6Author.Companion.where("id", 1L).first().get("name"));
    }
}
