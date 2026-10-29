package database.migrations

import org.konaframework.cadence.migrations.Migration
import org.konaframework.cadence.migrations.MigrationName
import org.konaframework.cadence.schema.Schema

@MigrationName("2026_10_29_120002_create_tasks_table")
class CreateTasksTable : Migration {
    override fun up(schema: Schema) {
        schema.create("tasks") { table ->
            table.id()
            table.foreignId("project_id").nullable().constrained().nullOnDelete()
            table.string("name")
            table.string("status", 32).default("open")
        }
    }

    override fun down(schema: Schema) {
        schema.dropIfExists("tasks")
    }
}
