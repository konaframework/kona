package config;

import org.konaframework.core.ConfigFile;
import org.konaframework.core.Env;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Database implements ConfigFile {
    @Override
    public String key() {
        return "database";
    }

    @Override
    public Map<String, Object> config() {
        Map<String, Object> main = new LinkedHashMap<>();
        main.put("url", Env.getOrDefault("DB_URL", "jdbc:h2:file:./storage/database/kona;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE"));
        main.put("username", Env.getOrDefault("DB_USERNAME", "sa"));
        main.put("password", Env.getOrDefault("DB_PASSWORD", ""));

        Map<String, Object> connections = new LinkedHashMap<>();
        connections.put("main", main);

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("default", Env.getOrDefault("DB_CONNECTION", "main"));
        values.put("connections", connections);
        return values;
    }
}
