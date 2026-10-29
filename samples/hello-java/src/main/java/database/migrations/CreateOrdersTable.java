package database.migrations;

import org.konaframework.cadence.migrations.Migration;
import org.konaframework.cadence.migrations.MigrationName;
import org.konaframework.cadence.schema.Schema;

@MigrationName("2026_10_29_120003_create_orders_table")
public class CreateOrdersTable implements Migration {
    @Override
    public void up(Schema schema) {
        schema.create("orders", table -> {
            table.id();
            table.string("name");
        });
    }

    @Override
    public void down(Schema schema) {
        schema.dropIfExists("orders");
    }
}
