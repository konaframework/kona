package config;

import dev.kona.core.Config;
import dev.kona.core.ConfigFile;
import dev.kona.core.Env;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Session implements ConfigFile {
    @Override
    public String key() {
        return "session";
    }

    @Override
    public Map<String, Object> config() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("driver", Env.getOrDefault("SESSION_DRIVER", "cookie"));
        try {
            values.put("lifetime", Integer.parseInt(Env.getOrDefault("SESSION_LIFETIME", "120")));
        } catch (NumberFormatException e) {
            values.put("lifetime", 120);
        }
        values.put("cookie", Env.getOrDefault("SESSION_COOKIE", "kona_session"));
        values.put("path", Env.getOrDefault("SESSION_PATH", "/"));
        values.put("encrypt", Boolean.TRUE.equals(Config.toBoolean(Env.getOrDefault("SESSION_ENCRYPT", "false"))));
        values.put("http_only", Boolean.TRUE.equals(Config.toBoolean(Env.getOrDefault("SESSION_HTTP_ONLY", "true"))));
        values.put("same_site", Env.getOrDefault("SESSION_SAME_SITE", "Lax"));
        return values;
    }
}
