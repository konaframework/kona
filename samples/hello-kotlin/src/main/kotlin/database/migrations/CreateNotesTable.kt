package database.migrations

import org.konaframework.cadence.migrations.Migration
import org.konaframework.cadence.migrations.MigrationName
import org.konaframework.cadence.schema.Schema

@MigrationName("2026_10_29_120000_create_notes_table")
class CreateNotesTable : Migration {
    override fun up(schema: Schema) {
        schema.create("notes") { table ->
            table.id()
            table.string("name")
            table.string("status", 32).default("open")
            table.dateTime("due").nullable()
        }
    }

    override fun down(schema: Schema) {
        schema.dropIfExists("notes")
    }
}
