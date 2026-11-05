package org.konaframework.testing

import org.konaframework.cadence.Database
import org.konaframework.cadence.migrations.MigrationRegistry
import org.konaframework.cadence.migrations.Migrator
import org.konaframework.core.Application
import java.util.Collections
import java.util.WeakHashMap
import javax.sql.DataSource

/** Pools already migrated in this run (weak, so a closed app's pool is forgotten). */
private val MIGRATED: MutableSet<DataSource> = Collections.synchronizedSet(
    Collections.newSetFromMap(WeakHashMap<DataSource, Boolean>()),
)

/**
 * Database lifecycle of one test (Laravel `RefreshDatabase` / `DatabaseTransactions`).
 *
 * [begin] runs the app's pending migrations (once per connection pool, so once per booted app
 * rather than once per test) and opens a transaction; [rollback] undoes everything the test
 * wrote. HTTP requests run on the test thread, so they join the same transaction.
 *
 * - [migrate] `true` is `RefreshDatabase`; `false` is `DatabaseTransactions` (schema already there).
 * - [lazily] defers both steps until [ensureStarted] (called by [KonaTestContext.http],
 *   [KonaTestContext.database] and the assertions), so tests that never touch the database skip it.
 *
 * Use the transaction from the test thread only; a request handled on another thread would not see
 * uncommitted rows.
 */
class TestDatabase(
    private val app: Application,
    private val migrate: Boolean,
    private val lazily: Boolean,
) {
    constructor(app: Application) : this(app, true, false)

    constructor(app: Application, migrate: Boolean) : this(app, migrate, false)
    private var started = false

    /** The app's [Database]. */
    fun database(): Database = app.resolve(Database::class.java)

    /** Assertions over [database]. */
    fun assertions(): DatabaseAssertions = DatabaseAssertions(database())

    /** Whether the transaction is open. */
    fun isStarted(): Boolean = started

    /** Start now, or leave it to [ensureStarted] when [lazily]. */
    fun begin() {
        if (!lazily) ensureStarted()
    }

    /** Migrate (when [migrate]) and open the transaction; does nothing when already started. */
    fun ensureStarted() {
        if (started) return
        if (migrate) migrateOnce()
        database().connection().beginTransaction()
        started = true
    }

    /** Roll back the test's transaction (all nesting levels). Does nothing when not started. */
    fun rollback() {
        if (!started) return
        started = false
        val connection = database().connection()
        if (connection.inTransaction()) connection.rollBack(0)
    }

    private fun migrateOnce() {
        if (!app.container.bound(MigrationRegistry::class.java)) return
        val connection = database().connection()
        val source = connection.dataSource
        if (MIGRATED.contains(source)) return
        Migrator.using(connection, app.resolve(MigrationRegistry::class.java), Migrator.DEFAULT_TABLE) { migrator ->
            migrator.install()
            migrator.migrate()
        }
        MIGRATED.add(source)
    }
}
