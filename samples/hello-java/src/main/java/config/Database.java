package config;

import org.konaframework.core.ConfigFile;
import org.konaframework.core.Env;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Database configuration ({@code config/database.php} shape).
 *
 * <p>{@code default} is the connection name Cadence uses unless a query names another.
 * Each entry under {@code connections} is a JDBC URL plus credentials.
 */
public final class Database implements ConfigFile {
    @Override
    public String key() {
        return "database";
    }

    @Override
    public Map<String, Object> config() {
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
        Map<String, Object> connections = new LinkedHashMap<>();
        connections.put("main", connection(
            "h2",
            Env.getOrDefault("DB_URL", "jdbc:h2:file:./storage/database/kona;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE"),
            Env.getOrDefault("DB_USERNAME", "sa"),
            Env.getOrDefault("DB_PASSWORD", "")
        ));
        connections.put("h2", connection(
            "h2",
            Env.getOrDefault("DB_URL", "jdbc:h2:file:./storage/database/kona;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE"),
            Env.getOrDefault("DB_USERNAME", "sa"),
            Env.getOrDefault("DB_PASSWORD", "")
        ));
        connections.put("sqlite", connection(
            "sqlite",
            Env.getOrDefault("DB_URL", "jdbc:sqlite:storage/database/database.sqlite"),
            Env.getOrDefault("DB_USERNAME", ""),
            Env.getOrDefault("DB_PASSWORD", "")
        ));
        connections.put("mysql", connection(
            "mysql",
            Env.getOrDefault(
                "DB_URL",
                "jdbc:mysql://" + Env.getOrDefault("DB_HOST", "127.0.0.1") + ":" + Env.getOrDefault("DB_PORT", "3306") + "/" + Env.getOrDefault("DB_DATABASE", "kona")
            ),
            Env.getOrDefault("DB_USERNAME", "root"),
            Env.getOrDefault("DB_PASSWORD", "")
        ));
        connections.put("mariadb", connection(
            "mariadb",
            Env.getOrDefault(
                "DB_URL",
                "jdbc:mariadb://" + Env.getOrDefault("DB_HOST", "127.0.0.1") + ":" + Env.getOrDefault("DB_PORT", "3306") + "/" + Env.getOrDefault("DB_DATABASE", "kona")
            ),
            Env.getOrDefault("DB_USERNAME", "root"),
            Env.getOrDefault("DB_PASSWORD", "")
        ));
        connections.put("pgsql", connection(
            "pgsql",
            Env.getOrDefault(
                "DB_URL",
                "jdbc:postgresql://" + Env.getOrDefault("DB_HOST", "127.0.0.1") + ":" + Env.getOrDefault("DB_PORT", "5432") + "/" + Env.getOrDefault("DB_DATABASE", "kona")
            ),
            Env.getOrDefault("DB_USERNAME", "kona"),
            Env.getOrDefault("DB_PASSWORD", "")
        ));
        connections.put("sqlsrv", connection(
            "sqlsrv",
            Env.getOrDefault(
                "DB_URL",
                "jdbc:sqlserver://" + Env.getOrDefault("DB_HOST", "127.0.0.1") + ":" + Env.getOrDefault("DB_PORT", "1433") + ";databaseName=" + Env.getOrDefault("DB_DATABASE", "kona") + ";encrypt=false"
            ),
            Env.getOrDefault("DB_USERNAME", "sa"),
            Env.getOrDefault("DB_PASSWORD", "")
        ));
        connections.put("oracle", connection(
            "oracle",
            Env.getOrDefault(
                "DB_URL",
                "jdbc:oracle:thin:@//" + Env.getOrDefault("DB_HOST", "127.0.0.1") + ":" + Env.getOrDefault("DB_PORT", "1521") + "/" + Env.getOrDefault("DB_DATABASE", "FREEPDB1")
            ),
            Env.getOrDefault("DB_USERNAME", "kona"),
            Env.getOrDefault("DB_PASSWORD", "")
        ));

        Map<String, Object> values = new LinkedHashMap<>();
        /*
        |--------------------------------------------------------------------------
        | Default Database Connection Name
        |--------------------------------------------------------------------------
        |
        | Here you may specify which of the database connections below you wish
        | to use as your default connection for database operations.
        |
        */
        values.put("default", Env.getOrDefault("DB_CONNECTION", "main"));
        values.put("connections", connections);

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
        Map<String, Object> migrations = new LinkedHashMap<>();
        migrations.put("table", Env.getOrDefault("DB_MIGRATIONS_TABLE", "migrations"));
        migrations.put("update_date_on_publish", true);
        values.put("migrations", migrations);
        return values;
    }

    private static Map<String, Object> connection(String driver, String url, String username, String password) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("driver", driver);
        map.put("url", url);
        map.put("username", username);
        map.put("password", password);
        return map;
    }
}
