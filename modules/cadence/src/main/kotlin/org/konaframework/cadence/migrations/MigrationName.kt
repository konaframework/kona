package org.konaframework.cadence.migrations

/**
 * Name recorded in `extract_migrations` and used for run order.
 *
 * Matches the migration **file stem** (`yyyy_MM_dd_HHmmss_snake_name`). The JVM class
 * stays PascalCase (`CreateUsersTable`) so editors do not flag underscores in the type name.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class MigrationName(val value: String)
