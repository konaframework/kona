package database.migrations

import org.konaframework.cadence.migrations.Migration
import org.konaframework.cadence.migrations.MigrationName
import org.konaframework.cadence.schema.Schema

@MigrationName("2026_10_29_120001_create_projects_table")
class CreateProjectsTable : Migration {
    override fun up(schema: Schema) {
        schema.create("projects") { table ->
            table.id()
            table.string("name")
            table.boolean("active").default(true)
        }
    }

    override fun down(schema: Schema) {
        schema.dropIfExists("projects")
    }
}
