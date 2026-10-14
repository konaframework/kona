package dev.kona.extract

import dev.kona.core.Config
import dev.kona.core.Env
import java.util.function.Function
import javax.sql.DataSource

/**
 * Connection manager: named connections, lazily pooled with HikariCP on first use.
 *
 * ```kotlin
 * val db = Database().addConnection("default", ConnectionConfig("jdbc:h2:mem:app")).setAsGlobal()
 * db.table("users").where("active", true).get()
 * ```
 */
class Database @JvmOverloads constructor(
    defaultConnection: String = DEFAULT,
) : AutoCloseable {
    @Volatile
    var defaultConnection: String = defaultConnection

    private val configs = linkedMapOf<String, ConnectionConfig>()
    private val resolved = linkedMapOf<String, DatabaseConnection>()

    /** Registers (or replaces) a configured connection; its pool opens on first [connection] call. */
    @Synchronized
    fun addConnection(name: String, config: ConnectionConfig): Database {
        resolved.remove(name)?.close()
        configs[name] = config
        return this
    }

    /** Registers (or replaces) a connection over an existing [DataSource]. */
    @JvmOverloads
    @Synchronized
    fun addConnection(name: String, dataSource: DataSource, grammar: Grammar = Grammar.ANSI): Database {
        resolved.remove(name)?.close()
        configs.remove(name)
        resolved[name] = DatabaseConnection(name, dataSource, grammar)
        return this
    }

    @Synchronized
    fun hasConnection(name: String): Boolean = configs.containsKey(name) || resolved.containsKey(name)

    @Synchronized
    fun connectionNames(): Set<String> = configs.keys + resolved.keys

    fun connection(): DatabaseConnection = connection(defaultConnection)

    @Synchronized
    fun connection(name: String): DatabaseConnection = resolved.getOrPut(name) {
        val config = configs[name] ?: throw ExtractException("Database connection [$name] not configured.")
        DatabaseConnection(name, config.createDataSource(name), config.grammar())
    }

    fun <R> withConnection(block: ConnectionCallback<R>): R = connection().withConnection(block)

    fun <R> withConnection(name: String, block: ConnectionCallback<R>): R = connection(name).withConnection(block)

    fun table(table: String): QueryBuilder = connection().table(table)

    fun <R> transaction(block: Function<DatabaseConnection, R>): R = connection().transaction(block)

    /** Closes and forgets the open pool for [name]; the config stays so it can reconnect. */
    @Synchronized
    fun disconnect(name: String) {
        resolved.remove(name)?.close()
    }

    /** Closes every open pool. */
    @Synchronized
    override fun close() {
        resolved.values.forEach { it.close() }
        resolved.clear()
    }

    /** Makes this manager the one used by [DB] and [Extract] models. */
    fun setAsGlobal(): Database {
        global = this
        return this
    }

    companion object {
        const val DEFAULT = "default"

        @Volatile
        private var global: Database? = null

        @JvmStatic
        fun global(): Database = global
            ?: throw ExtractException("No global Database; register ExtractServiceProvider or call Database.setAsGlobal().")

        @JvmStatic
        fun hasGlobal(): Boolean = global != null

        @JvmStatic
        fun clearGlobal() {
            global = null
        }

        /**
         * Builds from the `database` config section:
         * `database.default` (connection name, or an inline connection map) and `database.connections.*`.
         * With no connections configured, falls back to `DB_*` environment variables when `DB_URL` is set.
         */
        @JvmStatic
        fun fromConfig(config: Config): Database = fromMap(config.get<Map<String, Any?>>("database") ?: emptyMap<String, Any?>())

        @JvmStatic
        fun fromMap(map: Map<String, *>): Database {
            val default = map["default"]
            val database = Database(if (default is String) default else DEFAULT)
            if (default is Map<*, *>) {
                database.addConnection(DEFAULT, ConnectionConfig.fromMap(stringKeys(DEFAULT, default)))
            }
            val connections = map["connections"] as? Map<*, *> ?: emptyMap<String, Any?>()
            connections.forEach { (name, value) ->
                database.addConnection(name.toString(), ConnectionConfig.fromMap(stringKeys(name.toString(), value)))
            }
            if (database.connectionNames().isEmpty() && Env.get("DB_URL") != null) {
                database.addConnection(database.defaultConnection, ConnectionConfig.fromEnv(Function<String, String?> { Env.get(it) }))
            }
            return database
        }

        private fun stringKeys(name: String, value: Any?): Map<String, Any?> {
            if (value !is Map<*, *>) {
                throw ExtractException("Database connection [$name] must be a map of settings.")
            }
            return value.entries.associate { (k, v) -> k.toString() to v }
        }
    }
}
