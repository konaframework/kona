package dev.kona.extract

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import dev.kona.core.Config
import java.util.function.Function

/**
 * One named JDBC connection: url, optional credentials / driver class, and pool size.
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

    fun grammar(): Grammar = if (quoteIdentifiers) Grammar.forUrl(url) else Grammar.NONE

    internal fun createDataSource(name: String): HikariDataSource {
        val hikari = HikariConfig()
        hikari.poolName = "kona-extract-$name"
        hikari.jdbcUrl = url
        hikari.username = username
        hikari.password = password
        if (driver != null) {
            hikari.driverClassName = driver
        }
        hikari.maximumPoolSize = poolSize
        return HikariDataSource(hikari)
    }

    override fun toString(): String =
        "ConnectionConfig(url=$url, username=$username, driver=$driver, poolSize=$poolSize, quoteIdentifiers=$quoteIdentifiers)"

    companion object {
        const val DEFAULT_POOL_SIZE = 10

        @JvmStatic
        fun fromMap(map: Map<String, *>): ConnectionConfig {
            val url = map["url"] ?: throw ExtractException("Database connection is missing [url]")
            return ConnectionConfig(
                url = url.toString(),
                username = map["username"]?.toString(),
                password = map["password"]?.toString(),
                driver = map["driver"]?.toString(),
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

        private fun intOf(value: Any?): Int? = when (value) {
            is Number -> value.toInt()
            is String -> value.trim().toIntOrNull()
            else -> null
        }
    }
}
