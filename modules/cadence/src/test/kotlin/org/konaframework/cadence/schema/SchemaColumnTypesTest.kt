package org.konaframework.cadence.schema

import org.konaframework.cadence.Cadence
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

class SchemaColumnTypesTest {
    private fun ledger(): Blueprint = Blueprint("ledger").apply {
        char("code", 3)
        uuid()
        ulid()
        foreignUuid("account_uuid")
        foreignUlid("batch_ulid")
        enum("status", listOf("draft", "it's posted"))
        tinyInteger("tiny")
        smallInteger("small")
        unsignedTinyInteger("utiny")
        unsignedSmallInteger("usmall")
        unsignedInteger("uint")
        unsignedBigInteger("ubig")
        decimal("amount", 16, 4)
        decimal("price")
        unsignedDecimal("quantity", 12, 3)
        float("ratio", 24)
        float("weight")
        double("score")
        time("opens_at")
        year("fiscal_year")
    }

    private fun types(grammar: SchemaGrammar): Map<String, String> =
        ledger().columns.associate { it.name to grammar.type(it) }

    private fun columns(grammar: SchemaGrammar): Map<String, String> =
        ledger().columns.associate { it.name to grammar.column(it) }

    @Test
    fun blueprintRecordsLengthPrecisionScaleAndValues() {
        val cols = ledger().columns.associateBy { it.name }
        assertEquals(ColumnType.CHAR, cols.getValue("code").type)
        assertEquals(3, cols.getValue("code").length)
        assertEquals(36, cols.getValue("uuid").length)
        assertEquals(ColumnType.UUID, cols.getValue("account_uuid").type)
        assertEquals(26, cols.getValue("ulid").length)
        assertEquals(ColumnType.ULID, cols.getValue("batch_ulid").type)
        assertEquals(listOf("draft", "it's posted"), cols.getValue("status").allowed)
        assertEquals(16, cols.getValue("amount").precision)
        assertEquals(4, cols.getValue("amount").scale)
        assertEquals(8, cols.getValue("price").precision)
        assertEquals(2, cols.getValue("price").scale)
        assertEquals(53, cols.getValue("weight").precision)
        assertTrue(cols.getValue("quantity").isUnsigned)
        assertTrue(cols.getValue("ubig").isUnsigned)
        assertFalse(cols.getValue("amount").isUnsigned)
        assertEquals(255, Blueprint("t").char("c").length)
        assertTrue(ColumnType.DECIMAL.isNumeric)
        assertFalse(ColumnType.STRING.isNumeric)
    }

    @Test
    fun standardPostgresAndH2Types() {
        val standard = types(SchemaGrammar.STANDARD)
        assertEquals("CHAR(3)", standard["code"])
        assertEquals("CHAR(36)", standard["uuid"])
        assertEquals("CHAR(26)", standard["ulid"])
        assertEquals("VARCHAR(255)", standard["status"])
        assertEquals("SMALLINT", standard["tiny"])
        assertEquals("SMALLINT", standard["small"])
        assertEquals("INTEGER", standard["uint"])
        assertEquals("BIGINT", standard["ubig"])
        assertEquals("DECIMAL(16, 4)", standard["amount"])
        assertEquals("DECIMAL(8, 2)", standard["price"])
        assertEquals("FLOAT(24)", standard["ratio"])
        assertEquals("FLOAT(53)", standard["weight"])
        assertEquals("DOUBLE PRECISION", standard["score"])
        assertEquals("TIME", standard["opens_at"])
        assertEquals("INTEGER", standard["fiscal_year"])
        assertEquals(standard, types(SchemaGrammar.POSTGRES))
        assertEquals(standard + mapOf("tiny" to "TINYINT", "utiny" to "TINYINT"), types(SchemaGrammar.H2))

        val pg = columns(SchemaGrammar.POSTGRES)
        assertEquals(
            "\"status\" VARCHAR(255) NOT NULL CHECK (\"status\" IN ('draft', 'it''s posted'))",
            pg["status"],
        )
        assertEquals("\"quantity\" DECIMAL(12, 3) NOT NULL CHECK (\"quantity\" >= 0)", pg["quantity"])
        assertEquals("\"ubig\" BIGINT NOT NULL CHECK (\"ubig\" >= 0)", pg["ubig"])
        assertEquals("\"amount\" DECIMAL(16, 4) NOT NULL", pg["amount"])
    }

    @Test
    fun mysqlAndMariaDbUseNativeEnumAndUnsignedIntegers() {
        for (grammar in listOf(SchemaGrammar.MYSQL, SchemaGrammar.MARIADB)) {
            val types = types(grammar)
            assertEquals("CHAR(36)", types["uuid"])
            assertEquals("ENUM('draft', 'it''s posted')", types["status"])
            assertEquals("TINYINT", types["tiny"])
            assertEquals("SMALLINT", types["small"])
            assertEquals("TINYINT UNSIGNED", types["utiny"])
            assertEquals("SMALLINT UNSIGNED", types["usmall"])
            assertEquals("INTEGER UNSIGNED", types["uint"])
            assertEquals("BIGINT UNSIGNED", types["ubig"])
            assertEquals("DECIMAL(16, 4)", types["amount"])
            assertEquals("DECIMAL(12, 3)", types["quantity"])
            assertEquals("FLOAT(24)", types["ratio"])
            assertEquals("DOUBLE", types["score"])
            assertEquals("TIME", types["opens_at"])
            assertEquals("YEAR", types["fiscal_year"])
            val columns = columns(grammar)
            assertEquals("`status` ENUM('draft', 'it''s posted') NOT NULL", columns["status"])
            assertEquals("`ubig` BIGINT UNSIGNED NOT NULL", columns["ubig"])
            assertEquals("`quantity` DECIMAL(12, 3) NOT NULL CHECK (`quantity` >= 0)", columns["quantity"])
        }
    }

    @Test
    fun sqliteTypes() {
        val types = types(SchemaGrammar.SQLITE)
        assertEquals("CHAR(3)", types["code"])
        assertEquals("CHAR(36)", types["uuid"])
        assertEquals("VARCHAR(255)", types["status"])
        assertEquals("INTEGER", types["tiny"])
        assertEquals("INTEGER", types["small"])
        assertEquals("INTEGER", types["fiscal_year"])
        assertEquals("NUMERIC", types["amount"])
        assertEquals("FLOAT", types["ratio"])
        assertEquals("DOUBLE", types["score"])
        assertEquals("TIME", types["opens_at"])
        assertEquals("\"quantity\" NUMERIC NOT NULL CHECK (\"quantity\" >= 0)", columns(SchemaGrammar.SQLITE)["quantity"])
    }

    @Test
    fun oracleTypes() {
        val types = types(SchemaGrammar.ORACLE)
        assertEquals("CHAR(3)", types["code"])
        assertEquals("CHAR(36)", types["uuid"])
        assertEquals("CHAR(26)", types["ulid"])
        assertEquals("VARCHAR2(255)", types["status"])
        assertEquals("NUMBER(3)", types["tiny"])
        assertEquals("NUMBER(5)", types["small"])
        assertEquals("NUMBER(10)", types["uint"])
        assertEquals("NUMBER(19)", types["ubig"])
        assertEquals("NUMBER(4)", types["fiscal_year"])
        assertEquals("NUMBER(16, 4)", types["amount"])
        assertEquals("FLOAT(24)", types["ratio"])
        assertEquals("BINARY_DOUBLE", types["score"])
        assertEquals("DATE", types["opens_at"])
        assertEquals("\"status\" VARCHAR2(255) NOT NULL CHECK (\"status\" IN ('draft', 'it''s posted'))", columns(SchemaGrammar.ORACLE)["status"])
    }

    @Test
    fun sqlServerTypes() {
        val types = types(SchemaGrammar.SQLSERVER)
        assertEquals("NCHAR(3)", types["code"])
        assertEquals("NCHAR(36)", types["uuid"])
        assertEquals("NCHAR(26)", types["ulid"])
        assertEquals("NVARCHAR(255)", types["status"])
        assertEquals("TINYINT", types["tiny"])
        assertEquals("SMALLINT", types["small"])
        assertEquals("INT", types["uint"])
        assertEquals("INT", types["fiscal_year"])
        assertEquals("DECIMAL(16, 4)", types["amount"])
        assertEquals("FLOAT(24)", types["ratio"])
        assertEquals("DOUBLE PRECISION", types["score"])
        assertEquals("TIME", types["opens_at"])
        assertEquals("[uint] INT NOT NULL CHECK ([uint] >= 0)", columns(SchemaGrammar.SQLSERVER)["uint"])
    }

    @Test
    fun compositePrimaryKeyInCreateAndAlter() {
        val create = Blueprint("order_lines").apply {
            foreignId("order_id")
            integer("line")
            primary("order_id", "line")
        }
        assertEquals(PrimaryKeyDefinition("order_lines_order_id_line_primary", listOf("order_id", "line")), create.primaryKey)
        assertEquals(
            "CREATE TABLE \"order_lines\" (\"order_id\" BIGINT NOT NULL, \"line\" INTEGER NOT NULL, " +
                "CONSTRAINT \"order_lines_order_id_line_primary\" PRIMARY KEY (\"order_id\", \"line\"))",
            SchemaGrammar.POSTGRES.compileCreate(create).single(),
        )
        assertTrue(SchemaGrammar.MYSQL.compileCreate(create).single().contains("CONSTRAINT `order_lines_order_id_line_primary` PRIMARY KEY (`order_id`, `line`)"))

        val single = Blueprint("tags").apply { string("slug").primary() }
        assertEquals(listOf("slug"), single.primaryKey!!.columns)

        val alter = Blueprint("pairs").apply { primary(listOf("a", "b"), "pairs_pk") }
        assertEquals(listOf("ALTER TABLE \"pairs\" ADD CONSTRAINT \"pairs_pk\" PRIMARY KEY (\"a\", \"b\")"), SchemaGrammar.POSTGRES.compileAlter(alter))
        assertEquals(listOf("ALTER TABLE [pairs] ADD CONSTRAINT [pairs_pk] PRIMARY KEY ([a], [b])"), SchemaGrammar.SQLSERVER.compileAlter(alter))
        val sqlite = assertThrows(IllegalStateException::class.java) { SchemaGrammar.SQLITE.compileAlter(alter) }
        assertTrue(sqlite.message!!.contains("SQLite cannot add a primary key"))

        assertNull(Blueprint("plain").primaryKey)
        assertThrows(IllegalStateException::class.java) { alter.primary("c") }
        assertThrows(IllegalArgumentException::class.java) { Blueprint("t").primary() }
    }

    @Test
    fun addColumnSyntaxPerEngine() {
        val bp = Blueprint("ledger").apply { decimal("fee", 10, 2).nullable() }
        assertEquals(listOf("ALTER TABLE \"ledger\" ADD COLUMN \"fee\" DECIMAL(10, 2) NULL"), SchemaGrammar.POSTGRES.compileAlter(bp))
        assertEquals(listOf("ALTER TABLE \"ledger\" ADD COLUMN \"fee\" NUMERIC NULL"), SchemaGrammar.SQLITE.compileAlter(bp))
        assertEquals(listOf("ALTER TABLE `ledger` ADD COLUMN `fee` DECIMAL(10, 2) NULL"), SchemaGrammar.MYSQL.compileAlter(bp))
        assertEquals(listOf("ALTER TABLE [ledger] ADD [fee] DECIMAL(10, 2) NULL"), SchemaGrammar.SQLSERVER.compileAlter(bp))
        assertEquals(listOf("ALTER TABLE \"ledger\" ADD (\"fee\" NUMBER(10, 2) NULL)"), SchemaGrammar.ORACLE.compileAlter(bp))
    }

    @Test
    fun rejectsInvalidDefinitions() {
        val bp = Blueprint("t")
        assertThrows(IllegalArgumentException::class.java) { bp.char("c", 0) }
        assertThrows(IllegalArgumentException::class.java) { bp.decimal("d", 0, 0) }
        assertThrows(IllegalArgumentException::class.java) { bp.decimal("d", 4, 5) }
        assertThrows(IllegalArgumentException::class.java) { bp.decimal("d", 4, -1) }
        assertThrows(IllegalArgumentException::class.java) { bp.float("f", 0) }
        assertThrows(IllegalArgumentException::class.java) { bp.enum("e", emptyList()) }
        val error = assertThrows(IllegalArgumentException::class.java) { bp.string("s").unsigned() }
        assertEquals("Column [s] of type STRING cannot be unsigned", error.message)
        assertEquals(0, bp.decimal("whole", 10, 0).scale)
    }

    @Test
    fun constrainedPluralizesTheColumnStem() {
        val bp = Blueprint("invoices")
        assertEquals("currencies", bp.foreignId("currency_id").constrained().onTable)
        assertEquals("companies", bp.foreignId("company_id").constrained().onTable)
        assertEquals("categories", bp.foreignId("category_id").constrained().onTable)
        assertEquals("statuses", bp.foreignId("status_id").constrained().onTable)
        assertEquals("addresses", bp.foreignId("address_id").constrained().onTable)
        assertEquals("people", bp.foreignId("person_id").constrained().onTable)
        assertEquals("users", bp.foreignId("user_id").constrained().onTable)
        val byUuid = bp.foreignUuid("account_uuid").constrained(column = "uuid")
        assertEquals("accounts", byUuid.onTable)
        assertEquals("uuid", byUuid.referencesColumn)
        val manual = bp.foreignUlid("batch_ulid").references("ulid").on("batches")
        assertEquals("batches", manual.onTable)
        assertEquals("ulid", manual.referencesColumn)
        assertEquals("people", Cadence.tableNameFor("Person"))
        assertEquals("sales_people", Cadence.tableNameFor("SalesPerson"))
        assertEquals("currencies", Cadence.tableNameFor("Currency"))
    }

    @Test
    fun h2RoundTripEnforcesChecks() {
        DriverManager.getConnection("jdbc:h2:mem:types_${System.nanoTime()};DB_CLOSE_DELAY=-1").use { jdbc ->
            roundTrip(jdbc, Schema(jdbc))
        }
    }

    @Test
    fun sqliteRoundTripEnforcesChecks() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { jdbc ->
            roundTrip(jdbc, Schema(jdbc))
        }
    }

    private fun roundTrip(jdbc: Connection, schema: Schema) {
        schema.create("accounts") {
            uuid().primary()
            string("name")
        }
        schema.create("ledger") {
            char("code", 3)
            foreignUuid("account_uuid").constrained(column = "uuid")
            enum("status", listOf("draft", "posted")).default("draft")
            unsignedTinyInteger("tiny").default(1)
            smallInteger("small").default(-2)
            unsignedInteger("uint").default(3)
            unsignedBigInteger("ubig").default(4L)
            decimal("amount", 16, 4)
            unsignedDecimal("quantity", 12, 3).default(0)
            float("ratio").nullable()
            double("score").nullable()
            time("opens_at").nullable()
            year("fiscal_year").nullable()
            ulid().nullable()
            integer("line")
            primary("code", "line")
        }
        schema.table("ledger") { decimal("fee", 10, 2).nullable() }
        assertTrue(schema.hasColumn("ledger", "fee"))
        val account = "0190c6f2-8a5b-7c3d-9e4f-123456789abc"
        jdbc.createStatement().use { st ->
            st.execute("""INSERT INTO "accounts" ("uuid", "name") VALUES ('$account', 'Cash')""")
            st.execute(
                """INSERT INTO "ledger" ("code", "account_uuid", "amount", "ratio", "score", "opens_at", "fiscal_year", "line", "fee") """ +
                    """VALUES ('KES', '$account', 1234.5678, 0.5, 2.25, '09:30:00', 2026, 1, 1.5)""",
            )
            st.executeQuery("""SELECT "status", "tiny", "small", "uint", "ubig", "amount", "quantity", "score", "fiscal_year", "account_uuid" FROM "ledger"""").use { rs ->
                assertTrue(rs.next())
                assertEquals("draft", rs.getString(1))
                assertEquals(1, rs.getInt(2))
                assertEquals(-2, rs.getInt(3))
                assertEquals(3, rs.getInt(4))
                assertEquals(4L, rs.getLong(5))
                assertEquals(0, BigDecimal("1234.5678").compareTo(rs.getBigDecimal(6)))
                assertEquals(0, BigDecimal.ZERO.compareTo(rs.getBigDecimal(7)))
                assertEquals(2.25, rs.getDouble(8))
                assertEquals(2026, rs.getInt(9))
                assertEquals(account, rs.getString(10))
            }
            assertThrows(SQLException::class.java) {
                st.execute("""INSERT INTO "ledger" ("code", "account_uuid", "amount", "line", "status") VALUES ('USD', '$account', 1, 2, 'void')""")
            }
            assertThrows(SQLException::class.java) {
                st.execute("""INSERT INTO "ledger" ("code", "account_uuid", "amount", "line", "quantity") VALUES ('USD', '$account', 1, 3, -1)""")
            }
            assertThrows(SQLException::class.java) {
                st.execute("""INSERT INTO "ledger" ("code", "account_uuid", "amount", "line") VALUES ('KES', '$account', 1, 1)""")
            }
        }
    }
}
