package org.konaframework.cadence

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.konaframework.core.Config
import java.util.function.Function

/**
 * One named JDBC connection: url, optional credentials / driver, and pool size.
 *
 * [driver] may be a JDBC class (`org.sqlite.JDBC`) or an engine alias (`sqlite`, `h2`, `mysql`,
 * `mariadb`, `pgsql`, `sqlsrv`, `oracle`). Aliases are what the `kona new` scaffold writes;
 * Hikari receives the resolved class name. Blank / unknown aliases are omitted so Hikari can
 * load the driver from the JDBC URL.
 *
 * Map keys (config files): `url`, `username`, `password`, `driver`, `pool_size` (or `poolSize`),
 * `quote_identifiers` (or `quoteIdentifiers`, default true).
 * Env keys: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `DB_DRIVER`, `DB_POOL_SIZE`, `DB_QUOTE_IDENTIFIERS`.
 */
class ConnectionConfig @JvmOverloads constructor(
    val url: String,
    val username: String? = null,
    val password: String? = null,
    val driver: String? = null,
    val poolSize: Int = DEFAULT_POOL_SIZE,
    val quoteIdentifiers: Boolean = true,
) {
    init {
        require(poolSize >= 1) { "Database pool size must be at least 1, got $poolSize" }
    }

    /**
     * Identifier + query dialect for this URL. When [quoteIdentifiers] is false, returns the same
     * dialect with quoting disabled via [Grammar.unquoted].
     */
    fun grammar(): Grammar {
        val quoted = Grammar.forUrl(url)
        return if (quoteIdentifiers) quoted else quoted.unquoted()
    }

    internal fun createDataSource(name: String): HikariDataSource {
        val hikari = HikariConfig()
        hikari.poolName = "kona-cadence-$name"
        hikari.jdbcUrl = url
        hikari.username = username
        hikari.password = password
        val jdbcClass = jdbcDriverClassName()
        if (jdbcClass != null) {
            hikari.driverClassName = jdbcClass
        }
        hikari.maximumPoolSize = poolSize
        return HikariDataSource(hikari)
    }

    /**
     * JDBC class Hikari should load, or null to infer from [url].
     * Engine aliases from the scaffold (`sqlite`, `pgsql`, …) map to first-party driver classes.
     */
    fun jdbcDriverClassName(): String? {
        val configured = driver ?: return null
        val raw = configured.trim()
        if (raw.isEmpty()) {
            return null
        }
        if (raw.contains('.')) {
            return raw
        }
        return ENGINE_DRIVERS[raw.lowercase()]
    }

    override fun toString(): String =
        "ConnectionConfig(url=$url, username=$username, driver=$driver, poolSize=$poolSize, quoteIdentifiers=$quoteIdentifiers)"

    companion object {
        const val DEFAULT_POOL_SIZE = 10

        private val ENGINE_DRIVERS: Map<String, String> = mapOf(
            "sqlite" to "org.sqlite.JDBC",
            "h2" to "org.h2.Driver",
            "mysql" to "com.mysql.cj.jdbc.Driver",
            "mariadb" to "org.mariadb.jdbc.Driver",
            "pgsql" to "org.postgresql.Driver",
            "postgres" to "org.postgresql.Driver",
            "postgresql" to "org.postgresql.Driver",
            "pg" to "org.postgresql.Driver",
            "sqlsrv" to "com.microsoft.sqlserver.jdbc.SQLServerDriver",
            "sqlserver" to "com.microsoft.sqlserver.jdbc.SQLServerDriver",
            "mssql" to "com.microsoft.sqlserver.jdbc.SQLServerDriver",
            "oracle" to "oracle.jdbc.OracleDriver",
        )

        @JvmStatic
        fun fromMap(map: Map<String, *>): ConnectionConfig {
            val url = map["url"] ?: throw CadenceException("Database connection is missing [url]")
            return ConnectionConfig(
                url = url.toString(),
                username = map["username"]?.toString(),
                password = map["password"]?.toString(),
                driver = driverOf(map["driver"]),
                poolSize = intOf(map["pool_size"] ?: map["poolSize"]) ?: DEFAULT_POOL_SIZE,
                quoteIdentifiers = Config.toBoolean(map["quote_identifiers"] ?: map["quoteIdentifiers"]) ?: true,
            )
        }

        /** Builds from `DB_*` variables resolved through [lookup] (e.g. `Env::get`). */
        @JvmStatic
        fun fromEnv(lookup: Function<String, String?>): ConnectionConfig = fromMap(
            mapOf(
                "url" to lookup.apply("DB_URL"),
                "username" to lookup.apply("DB_USERNAME"),
                "password" to lookup.apply("DB_PASSWORD"),
                "driver" to lookup.apply("DB_DRIVER"),
                "pool_size" to lookup.apply("DB_POOL_SIZE"),
                "quote_identifiers" to lookup.apply("DB_QUOTE_IDENTIFIERS"),
            ),
        )

        @JvmStatic
        fun fromEnv(env: Map<String, String?>): ConnectionConfig = fromEnv(Function<String, String?> { env[it] })

        private fun driverOf(value: Any?): String? {
            if (value == null) {
                return null
            }
            val raw = value.toString().trim()
            return if (raw.isEmpty()) null else raw
        }

        private fun intOf(value: Any?): Int? = when (value) {
            is Number -> value.toInt()
            is String -> value.trim().toIntOrNull()
            else -> null
        }
    }
}
