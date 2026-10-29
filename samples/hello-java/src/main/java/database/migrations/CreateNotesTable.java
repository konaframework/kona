package database.migrations;

import org.konaframework.cadence.migrations.Migration;
import org.konaframework.cadence.migrations.MigrationName;
import org.konaframework.cadence.schema.Schema;

@MigrationName("2026_10_29_120000_create_notes_table")
public class CreateNotesTable implements Migration {
    @Override
    public void up(Schema schema) {
        schema.create("notes", table -> {
            table.id();
            table.string("name");
            table.string("status", 32).withDefault("open");
            table.dateTime("due").nullable();
        });
    }

    @Override
    public void down(Schema schema) {
        schema.dropIfExists("notes");
    }
}
