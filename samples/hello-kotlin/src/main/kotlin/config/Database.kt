package config

import org.konaframework.core.ConfigFile
import org.konaframework.core.Env

/**
 * Database configuration (`config/database.php` shape).
 *
 * `default` is the connection name Cadence uses unless a query names another.
 * Each entry under `connections` is a JDBC URL plus credentials. Override with
 * `DB_URL` / `DB_USERNAME` / `DB_PASSWORD`, or pick a named engine via
 * `DB_CONNECTION` (`kona new --database=…`).
 */
object Database : ConfigFile {
    override fun key(): String = "database"

    override fun config(): Map<String, Any?> = mapOf(
        /*
        |--------------------------------------------------------------------------
        | Default Database Connection Name
        |--------------------------------------------------------------------------
        |
        | Here you may specify which of the database connections below you wish
        | to use as your default connection for database operations. This is
        | the connection which will be utilized unless another connection
        | is explicitly specified when you execute a query / statement.
        |
        */
        "default" to Env.getOrDefault("DB_CONNECTION", "main"),

        /*
        |--------------------------------------------------------------------------
        | Database Connections
        |--------------------------------------------------------------------------
        |
        | Below are all of the database connections defined for your application.
        | An example configuration is provided for each database system which
        | is supported by Kona. You're free to add / remove connections.
        |
        | Supported: sqlite, h2/main, mysql, mariadb, pgsql, sqlsrv, oracle
        |
        */
        "connections" to mapOf(
            "main" to mapOf(
                "driver" to "h2",
                "url" to Env.getOrDefault("DB_URL", "jdbc:h2:file:./storage/database/kona;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE"),
                "username" to Env.getOrDefault("DB_USERNAME", "sa"),
                "password" to Env.getOrDefault("DB_PASSWORD", ""),
            ),
            "h2" to mapOf(
                "driver" to "h2",
                "url" to Env.getOrDefault("DB_URL", "jdbc:h2:file:./storage/database/kona;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE"),
                "username" to Env.getOrDefault("DB_USERNAME", "sa"),
                "password" to Env.getOrDefault("DB_PASSWORD", ""),
            ),
            "sqlite" to mapOf(
                "driver" to "sqlite",
                "url" to Env.getOrDefault("DB_URL", "jdbc:sqlite:storage/database/database.sqlite"),
                "username" to Env.getOrDefault("DB_USERNAME", ""),
                "password" to Env.getOrDefault("DB_PASSWORD", ""),
            ),
            "mysql" to mapOf(
                "driver" to "mysql",
                "url" to Env.getOrDefault(
                    "DB_URL",
                    "jdbc:mysql://${Env.getOrDefault("DB_HOST", "127.0.0.1")}:${Env.getOrDefault("DB_PORT", "3306")}/${Env.getOrDefault("DB_DATABASE", "kona")}",
                ),
                "username" to Env.getOrDefault("DB_USERNAME", "root"),
                "password" to Env.getOrDefault("DB_PASSWORD", ""),
            ),
            "mariadb" to mapOf(
                "driver" to "mariadb",
                "url" to Env.getOrDefault(
                    "DB_URL",
                    "jdbc:mariadb://${Env.getOrDefault("DB_HOST", "127.0.0.1")}:${Env.getOrDefault("DB_PORT", "3306")}/${Env.getOrDefault("DB_DATABASE", "kona")}",
                ),
                "username" to Env.getOrDefault("DB_USERNAME", "root"),
                "password" to Env.getOrDefault("DB_PASSWORD", ""),
            ),
            "pgsql" to mapOf(
                "driver" to "pgsql",
                "url" to Env.getOrDefault(
                    "DB_URL",
                    "jdbc:postgresql://${Env.getOrDefault("DB_HOST", "127.0.0.1")}:${Env.getOrDefault("DB_PORT", "5432")}/${Env.getOrDefault("DB_DATABASE", "kona")}",
                ),
                "username" to Env.getOrDefault("DB_USERNAME", "kona"),
                "password" to Env.getOrDefault("DB_PASSWORD", ""),
            ),
            "sqlsrv" to mapOf(
                "driver" to "sqlsrv",
                "url" to Env.getOrDefault(
                    "DB_URL",
                    "jdbc:sqlserver://${Env.getOrDefault("DB_HOST", "127.0.0.1")}:${Env.getOrDefault("DB_PORT", "1433")};databaseName=${Env.getOrDefault("DB_DATABASE", "kona")};encrypt=false",
                ),
                "username" to Env.getOrDefault("DB_USERNAME", "sa"),
                "password" to Env.getOrDefault("DB_PASSWORD", ""),
            ),
            "oracle" to mapOf(
                "driver" to "oracle",
                "url" to Env.getOrDefault(
                    "DB_URL",
                    "jdbc:oracle:thin:@//${Env.getOrDefault("DB_HOST", "127.0.0.1")}:${Env.getOrDefault("DB_PORT", "1521")}/${Env.getOrDefault("DB_DATABASE", "FREEPDB1")}",
                ),
                "username" to Env.getOrDefault("DB_USERNAME", "kona"),
                "password" to Env.getOrDefault("DB_PASSWORD", ""),
            ),
        ),

        /*
        |--------------------------------------------------------------------------
        | Migration Repository Table
        |--------------------------------------------------------------------------
        |
        | This table keeps track of all the migrations that have already run for
        | your application. Using this information, we can determine which of
        | the migrations on disk haven't actually been run on the database.
        |
        */
        "migrations" to mapOf(
            "table" to Env.getOrDefault("DB_MIGRATIONS_TABLE", "migrations"),
            "update_date_on_publish" to true,
        ),
    )
}
