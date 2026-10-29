package database.migrations

import org.konaframework.cadence.migrations.Migration
import org.konaframework.cadence.migrations.MigrationName
import org.konaframework.cadence.schema.Schema

@MigrationName("2026_10_29_120003_create_orders_table")
class CreateOrdersTable : Migration {
    override fun up(schema: Schema) {
        schema.create("orders") { table ->
            table.id()
            table.string("name")
        }
    }

    override fun down(schema: Schema) {
        schema.dropIfExists("orders")
    }
}
