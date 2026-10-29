package database.migrations;

import org.konaframework.cadence.migrations.Migration;
import org.konaframework.cadence.migrations.MigrationName;
import org.konaframework.cadence.schema.Schema;

@MigrationName("2026_10_29_120001_create_projects_table")
public class CreateProjectsTable implements Migration {
    @Override
    public void up(Schema schema) {
        schema.create("projects", table -> {
            table.id();
            table.string("name");
            table.bool("active").withDefault(true);
        });
    }

    @Override
    public void down(Schema schema) {
        schema.dropIfExists("projects");
    }
}
