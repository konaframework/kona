package org.konaframework.cli

/**
 * First-party JDBC engines for `kona new --database=` and scaffold connection maps.
 * Coordinates match [gradle/libs.versions.toml] / `kona-bom` pins.
 */
object DatabaseEngines {
    data class Engine(
        val name: String,
        val connectionKey: String,
        val defaultUrl: String,
        val defaultUsername: String,
        val defaultPassword: String,
        /** Gradle `runtimeOnly("…")` coordinate, or null when H2 is already on every skeleton. */
        val runtimeDependency: String?,
    )

    val ALL: List<Engine> = listOf(
        Engine(
            name = "h2",
            connectionKey = "h2",
            defaultUrl = "jdbc:h2:mem:kona;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
            defaultUsername = "sa",
            defaultPassword = "",
            runtimeDependency = null,
        ),
        Engine(
            name = "sqlite",
            connectionKey = "sqlite",
            defaultUrl = "jdbc:sqlite:database/database.sqlite",
            defaultUsername = "",
            defaultPassword = "",
            runtimeDependency = "org.xerial:sqlite-jdbc:3.49.1.0",
        ),
        Engine(
            name = "mysql",
            connectionKey = "mysql",
            defaultUrl = "jdbc:mysql://127.0.0.1:3306/kona",
            defaultUsername = "root",
            defaultPassword = "",
            runtimeDependency = "com.mysql:mysql-connector-j:9.2.0",
        ),
        Engine(
            name = "mariadb",
            connectionKey = "mariadb",
            defaultUrl = "jdbc:mariadb://127.0.0.1:3306/kona",
            defaultUsername = "root",
            defaultPassword = "",
            runtimeDependency = "org.mariadb.jdbc:mariadb-java-client:3.5.2",
        ),
        Engine(
            name = "pgsql",
            connectionKey = "pgsql",
            defaultUrl = "jdbc:postgresql://127.0.0.1:5432/kona",
            defaultUsername = "kona",
            defaultPassword = "",
            runtimeDependency = "org.postgresql:postgresql:42.7.5",
        ),
        Engine(
            name = "sqlsrv",
            connectionKey = "sqlsrv",
            defaultUrl = "jdbc:sqlserver://127.0.0.1:1433;databaseName=kona;encrypt=false",
            defaultUsername = "sa",
            defaultPassword = "",
            runtimeDependency = "com.microsoft.sqlserver:mssql-jdbc:12.8.1.jre11",
        ),
        Engine(
            name = "oracle",
            connectionKey = "oracle",
            defaultUrl = "jdbc:oracle:thin:@//127.0.0.1:1521/FREEPDB1",
            defaultUsername = "kona",
            defaultPassword = "",
            runtimeDependency = "com.oracle.database.jdbc:ojdbc11:23.7.0.25.01",
        ),
    )

    private val byName = ALL.associateBy { it.name }

    fun names(): List<String> = ALL.map { it.name }

    fun require(name: String): Engine =
        byName[name.lowercase()]
            ?: throw KonaCliException("--database must be one of: ${names().joinToString(", ")}")

    /** Aliases accepted by `--database=` (postgres → pgsql, sqlserver → sqlsrv). */
    fun resolve(raw: String): Engine {
        val key = when (raw.lowercase()) {
            "postgres", "postgresql", "pg" -> "pgsql"
            "sqlserver", "mssql" -> "sqlsrv"
            "maria" -> "mariadb"
            else -> raw.lowercase()
        }
        return require(key)
    }
}
