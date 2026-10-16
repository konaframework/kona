package config;

import io.konaframework.core.ConfigFile;
import io.konaframework.core.Env;
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
        main.put("url", Env.getOrDefault("DB_URL", "jdbc:h2:mem:kona;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"));
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
