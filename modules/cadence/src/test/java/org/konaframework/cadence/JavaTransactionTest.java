package org.konaframework.cadence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class JavaTransactionTest {
    @AfterEach
    void reset() {
        Database.clearGlobal();
    }

    @Test
    void transactionRetriesSavepointsAndAfterCommitFromJava() {
        DatabaseConnection connection = TestDb.INSTANCE.create().connection();
        AtomicInteger attempts = new AtomicInteger();
        String result = connection.transaction(tx -> {
            if (attempts.incrementAndGet() < 2) {
                throw new QueryException(
                    "select 1",
                    List.of(),
                    new SQLException("deadlock detected", "40001"));
            }
            tx.table("tags").insert(Map.of("code", "j1", "label", "ok"));
            return "ok";
        }, 3);
        assertEquals("ok", result);
        assertEquals(2, attempts.get());

        List<String> log = new ArrayList<>();
        connection.transaction(outer -> {
            outer.afterCommit(() -> log.add("outer"));
            try {
                outer.transaction(inner -> {
                    inner.table("tags").insert(Map.of("code", "drop", "label", "x"));
                    inner.afterCommit(() -> log.add("inner-drop"));
                    throw new IllegalStateException("inner");
                });
            } catch (IllegalStateException ignored) {
                // savepoint rolled back
            }
            outer.afterCommit(() -> log.add("kept"));
            outer.table("tags").insert(Map.of("code", "j2", "label", "ok"));
            return null;
        });
        assertEquals(List.of("outer", "kept"), log);
        assertEquals(2L, connection.table("tags").count());

        DB.beginTransaction();
        assertEquals(1, DB.transactionLevel());
        DB.table("tags").insert(Map.of("code", "j3", "label", "m"));
        DB.rollBack();
        assertEquals(0, DB.transactionLevel());
        assertEquals(2L, DB.table("tags").count());

        Database database = Database.global();
        database.beginTransaction();
        database.rollBack();
        assertEquals(0, database.transactionLevel());
        assertEquals("java", database.transaction(tx -> "java", 2));

        assertFalse(ConcurrencyErrorDetector.causedByConcurrencyError(new IllegalStateException("nope")));
        assertTrue(ConcurrencyErrorDetector.causedByConcurrencyError(new SQLException("x", "40001")));
    }
}
