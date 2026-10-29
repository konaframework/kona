package database.migrations;

import org.konaframework.cadence.migrations.Migration;
import org.konaframework.cadence.migrations.MigrationName;
import org.konaframework.cadence.schema.Schema;

@MigrationName("2026_10_29_120004_create_lines_table")
public class CreateLinesTable implements Migration {
    @Override
    public void up(Schema schema) {
        schema.create("lines", table -> {
            table.id();
            table.foreignId("order_id").nullable().constrained("orders").cascadeOnDelete();
            table.string("sku", 64);
        });
    }

    @Override
    public void down(Schema schema) {
        schema.dropIfExists("lines");
    }
}
