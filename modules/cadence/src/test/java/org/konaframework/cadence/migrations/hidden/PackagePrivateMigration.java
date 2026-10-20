package org.konaframework.cadence.migrations.hidden;

import org.konaframework.cadence.migrations.Migration;
import org.konaframework.cadence.migrations.MigrationName;
import org.konaframework.cadence.schema.Schema;

@MigrationName("2026_10_20_090000_package_private")
class PackagePrivateMigration implements Migration {
    @Override
    public void up(Schema schema) {}

    @Override
    public void down(Schema schema) {}
}
