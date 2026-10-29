package database.migrations

import org.konaframework.cadence.migrations.Migration
import org.konaframework.cadence.migrations.MigrationName
import org.konaframework.cadence.schema.Schema

@MigrationName("2026_10_29_120004_create_lines_table")
class CreateLinesTable : Migration {
    override fun up(schema: Schema) {
        schema.create("lines") { table ->
            table.id()
            table.foreignId("order_id").nullable().constrained("orders").cascadeOnDelete()
            table.string("sku", 64)
        }
    }

    override fun down(schema: Schema) {
        schema.dropIfExists("lines")
    }
}
