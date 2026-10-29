package database.migrations;

import org.konaframework.cadence.migrations.Migration;
import org.konaframework.cadence.migrations.MigrationName;
import org.konaframework.cadence.schema.Schema;

@MigrationName("2026_10_29_120002_create_tasks_table")
public class CreateTasksTable implements Migration {
    @Override
    public void up(Schema schema) {
        schema.create("tasks", table -> {
            table.id();
            table.foreignId("project_id").nullable().constrained().nullOnDelete();
            table.string("name");
            table.string("status", 32).withDefault("open");
        });
    }

    @Override
    public void down(Schema schema) {
        schema.dropIfExists("tasks");
    }
}
