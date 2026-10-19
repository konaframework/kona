package org.konaframework.cadence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.konaframework.cadence.schema.OracleSchemaGrammar;
import org.konaframework.cadence.schema.PostgresSchemaGrammar;
import org.konaframework.cadence.schema.SchemaGrammar;
import org.konaframework.cadence.schema.SqlServerSchemaGrammar;
import org.junit.jupiter.api.Test;

/** Java-callable regression for M25 engine registries. */
public class JavaM25EnginesTest {
    @Test
    void grammarAndSchemaDetectionFromJava() {
        assertSame(Grammar.SQLSERVER, Grammar.forUrl("jdbc:sqlserver://localhost;databaseName=kona"));
        assertSame(Grammar.ORACLE, Grammar.forUrl("jdbc:oracle:thin:@//localhost/x"));
        assertSame(Grammar.POSTGRES, Grammar.forUrl("jdbc:postgresql://localhost/kona"));
        assertEquals(Dialect.MYSQL, Grammar.dialectForProduct("MySQL"));
        assertTrue(SchemaGrammar.forProduct("PostgreSQL") instanceof PostgresSchemaGrammar);
        assertTrue(SchemaGrammar.forProduct("Oracle") instanceof OracleSchemaGrammar);
        assertTrue(SchemaGrammar.forProduct("Microsoft SQL Server") instanceof SqlServerSchemaGrammar);
        assertEquals(" for share", Grammar.POSTGRES.compileLock(LockMode.SHARED));
        assertTrue(Grammar.SQLSERVER.compileOrderLimit(java.util.List.of(), 10, 0, false).contains("fetch next"));
        ConnectionConfig cfg = new ConnectionConfig("jdbc:postgresql://127.0.0.1/kona", "kona", "");
        assertEquals(Dialect.POSTGRES, cfg.grammar().getDialect());
        // Cover both @JvmOverloads Grammar constructors from Java.
        Grammar twoArg = new Grammar("\"", "\"");
        Grammar threeArg = new Grammar("\"", "\"", Dialect.H2);
        assertEquals(Dialect.STANDARD, twoArg.getDialect());
        assertEquals(Dialect.H2, threeArg.getDialect());
        assertEquals("\"t\"", twoArg.wrap("t"));
        for (Dialect dialect : Dialect.values()) {
            assertEquals(dialect == Dialect.STANDARD ? Dialect.STANDARD : dialect, Grammar.of(dialect).getDialect());
        }
        assertThrows(NullPointerException.class, () -> Grammar.of(null));
        assertThrows(NullPointerException.class, () -> GrammarLookup.of(null));
    }
}
