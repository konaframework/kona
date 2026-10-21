package org.konaframework.cadence

/**
 * First-party JDBC engines: class names Hikari should load, and the Gradle
 * `runtimeOnly` coordinate a Kona app needs when that JAR is not on the classpath.
 *
 * `kona new --database=pgsql` adds the coordinate at scaffold time. Switching
 * `DB_CONNECTION` later does not; Cadence then throws [CadenceException] with
 * the line to paste instead of Hikari's raw `RuntimeException`.
 */
object JdbcDrivers {
    data class Engine(
        val alias: String,
        val className: String,
        val gradle: String,
        val jdbcPrefix: String,
    )

    @JvmField
    val ENGINES: List<Engine> =
        listOf(
            Engine("sqlite", "org.sqlite.JDBC", "org.xerial:sqlite-jdbc:3.49.1.0", "jdbc:sqlite:"),
            Engine("h2", "org.h2.Driver", "com.h2database:h2:2.3.232", "jdbc:h2:"),
            Engine("mysql", "com.mysql.cj.jdbc.Driver", "com.mysql:mysql-connector-j:9.2.0", "jdbc:mysql:"),
            Engine("mariadb", "org.mariadb.jdbc.Driver", "org.mariadb.jdbc:mariadb-java-client:3.5.2", "jdbc:mariadb:"),
            Engine("pgsql", "org.postgresql.Driver", "org.postgresql:postgresql:42.7.5", "jdbc:postgresql:"),
            Engine(
                "sqlsrv",
                "com.microsoft.sqlserver.jdbc.SQLServerDriver",
                "com.microsoft.sqlserver:mssql-jdbc:12.8.1.jre11",
                "jdbc:sqlserver:",
            ),
            Engine("oracle", "oracle.jdbc.OracleDriver", "com.oracle.database.jdbc:ojdbc11:23.7.0.25.01", "jdbc:oracle:"),
        )

    private val byAlias: Map<String, Engine> =
        buildMap {
            ENGINES.forEach { put(it.alias, it) }
            put("postgres", ENGINES.first { it.alias == "pgsql" })
            put("postgresql", ENGINES.first { it.alias == "pgsql" })
            put("pg", ENGINES.first { it.alias == "pgsql" })
            put("sqlserver", ENGINES.first { it.alias == "sqlsrv" })
            put("mssql", ENGINES.first { it.alias == "sqlsrv" })
            put("maria", ENGINES.first { it.alias == "mariadb" })
        }

    private val byClass: Map<String, Engine> = ENGINES.associateBy { it.className }

    @JvmStatic
    fun className(alias: String): String? = byAlias[alias.lowercase()]?.className

    @JvmStatic
    @JvmOverloads
    fun gradleCoordinate(
        configured: String? = null,
        jdbcClass: String? = null,
        url: String? = null,
    ): String? {
        engineOf(configured)?.let { return it.gradle }
        jdbcClass?.let { byClass[it]?.let { engine -> return engine.gradle } }
        if (url == null) {
            return null
        }
        val prefix = url.lowercase()
        return ENGINES.firstOrNull { prefix.startsWith(it.jdbcPrefix) }?.gradle
    }

    @JvmStatic
    fun missingDriver(
        jdbcClass: String?,
        configured: String? = null,
        url: String? = null,
        cause: Throwable? = null,
    ): CadenceException {
        val classLabel = jdbcClass?.takeIf { it.isNotBlank() } ?: configured?.takeIf { it.contains('.') }
        val engine = engineOf(configured) ?: classLabel?.let { byClass[it] }
        val coord = gradleCoordinate(configured, classLabel, url)
        val who = classLabel ?: configured ?: url ?: "(unknown)"
        val body =
            buildString {
                append("Failed to load JDBC driver $who")
                if (engine != null) {
                    append(" for engine [${engine.alias}]")
                }
                append('.')
                if (coord != null) {
                    append(" Add this to build.gradle.kts, then re-run ./grind migrate:\n\n")
                    append("    runtimeOnly(\"$coord\")")
                } else {
                    append(" Add the matching JDBC JAR as runtimeOnly on the application classpath.")
                }
            }
        return CadenceException(body, cause)
    }

    internal fun isDriverLoadFailure(ex: Throwable): Boolean {
        var cur: Throwable? = ex
        while (cur != null) {
            if (cur is ClassNotFoundException || cur is NoClassDefFoundError) {
                return true
            }
            val msg = cur.message ?: ""
            if (msg.contains("Failed to load driver class", ignoreCase = true) ||
                msg.contains("Failed to get driver instance", ignoreCase = true) ||
                msg.contains("Failed to find driver", ignoreCase = true)
            ) {
                return true
            }
            cur = cur.cause
        }
        return false
    }

    private fun engineOf(configured: String?): Engine? {
        if (configured.isNullOrBlank()) {
            return null
        }
        val raw = configured.trim()
        byAlias[raw.lowercase()]?.let { return it }
        return byClass[raw]
    }
}
