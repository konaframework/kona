package org.konaframework.cadence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.konaframework.cadence.schema.Blueprint;
import org.konaframework.cadence.schema.ColumnDefinition;
import org.konaframework.cadence.schema.ForeignKeyDefinition;
import org.konaframework.cadence.schema.PrimaryKeyDefinition;
import org.konaframework.cadence.schema.Schema;
import org.konaframework.cadence.schema.SchemaGrammar;

/** Java-callable regression for the Blueprint column types, unsigned modifiers, and composite keys. */
public class JavaSchemaColumnTypesTest {
    @Test
    void columnTypesFromJava() {
        Blueprint table = new Blueprint("ledger");
        assertEquals("CHAR(255)", SchemaGrammar.STANDARD.type(table.charColumn("code")));
        assertEquals("CHAR(3)", SchemaGrammar.STANDARD.type(table.charColumn("currency", 3)));
        assertEquals("CHAR(36)", SchemaGrammar.STANDARD.type(table.uuid()));
        assertEquals("CHAR(36)", SchemaGrammar.STANDARD.type(table.uuid("external_id")));
        assertEquals("CHAR(26)", SchemaGrammar.STANDARD.type(table.ulid()));
        assertEquals("CHAR(26)", SchemaGrammar.STANDARD.type(table.ulid("trace_id")));
        assertEquals("ENUM('draft', 'posted')",
            SchemaGrammar.MYSQL.type(table.enumColumn("status", List.of("draft", "posted"))));
        assertEquals("DECIMAL(8, 2)", SchemaGrammar.STANDARD.type(table.decimal("price")));
        assertEquals("DECIMAL(16, 2)", SchemaGrammar.STANDARD.type(table.decimal("total", 16)));
        assertEquals("DECIMAL(16, 4)", SchemaGrammar.STANDARD.type(table.decimal("amount", 16, 4)));
        assertTrue(table.unsignedDecimal("qty").isUnsigned());
        assertEquals("DECIMAL(10, 2)", SchemaGrammar.STANDARD.type(table.unsignedDecimal("stock", 10)));
        assertEquals("DECIMAL(12, 3)", SchemaGrammar.STANDARD.type(table.unsignedDecimal("weight", 12, 3)));
        assertEquals("FLOAT(53)", SchemaGrammar.STANDARD.type(table.floatColumn("ratio")));
        assertEquals("FLOAT(24)", SchemaGrammar.STANDARD.type(table.floatColumn("share", 24)));
        assertEquals("DOUBLE PRECISION", SchemaGrammar.STANDARD.type(table.doubleColumn("score")));
        assertEquals("TINYINT UNSIGNED", SchemaGrammar.MYSQL.type(table.unsignedTinyInteger("a")));
        assertEquals("SMALLINT UNSIGNED", SchemaGrammar.MYSQL.type(table.unsignedSmallInteger("b")));
        assertEquals("INTEGER UNSIGNED", SchemaGrammar.MYSQL.type(table.unsignedInteger("c")));
        assertEquals("BIGINT UNSIGNED", SchemaGrammar.MYSQL.type(table.unsignedBigInteger("d")));
        assertEquals("BIGINT UNSIGNED", SchemaGrammar.MYSQL.type(table.bigInteger("e").unsigned()));
        assertEquals("TINYINT", SchemaGrammar.MYSQL.type(table.tinyInteger("f")));
        assertEquals("SMALLINT", SchemaGrammar.MYSQL.type(table.smallInteger("g")));
        assertEquals("TIME", SchemaGrammar.MYSQL.type(table.time("opens_at")));
        assertEquals("YEAR", SchemaGrammar.MYSQL.type(table.year("fiscal_year")));

        ColumnDefinition account = table.foreignUuid("account_uuid");
        ForeignKeyDefinition fk = account.constrained("accounts", "uuid");
        assertEquals("accounts", fk.getOnTable());
        assertEquals("currencies", table.foreignId("currency_id").constrained().getOnTable());
        assertEquals("batches", table.foreignUlid("batch_ulid").references("ulid").on("batches").getOnTable());

        PrimaryKeyDefinition key = table.primary("code", "currency");
        assertEquals(List.of("code", "currency"), key.getColumns());
        assertEquals("ledger_code_currency_primary", key.getName());
        Blueprint named = new Blueprint("pairs");
        assertEquals("pairs_pk", named.primary(List.of("a", "b"), "pairs_pk").getName());
    }

    @Test
    void decimalRoundTripsFromJava() throws Exception {
        try (Connection jdbc = DriverManager.getConnection("jdbc:h2:mem:java_types_" + System.nanoTime())) {
            Schema schema = new Schema(jdbc);
            schema.create("prices", table -> {
                table.charColumn("sku", 8);
                table.decimal("unit_price", 16, 4);
                table.enumColumn("kind", List.of("goods", "service")).withDefault("goods");
                table.primary("sku");
            });
            try (Statement st = jdbc.createStatement()) {
                st.execute("INSERT INTO \"prices\" (\"sku\", \"unit_price\") VALUES ('A1', 19.9950)");
                try (ResultSet rs = st.executeQuery("SELECT \"unit_price\", \"kind\" FROM \"prices\"")) {
                    assertTrue(rs.next());
                    assertEquals(0, new BigDecimal("19.9950").compareTo(rs.getBigDecimal(1)));
                    assertEquals("goods", rs.getString(2));
                }
            }
        }
    }
}
