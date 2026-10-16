package config;

import io.konaframework.core.Config;
import io.konaframework.core.ConfigFile;
import io.konaframework.core.Env;
import java.util.LinkedHashMap;
import java.util.Map;

public final class App implements ConfigFile {
    @Override
    public String key() {
        return "app";
    }

    @Override
    public Map<String, Object> config() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", Env.getOrDefault("APP_NAME", "hello-java"));
        values.put("env", Env.getOrDefault("APP_ENV", "local"));
        values.put("debug", Config.toBoolean(Env.getOrDefault("APP_DEBUG", "true")));
        values.put("url", Env.getOrDefault("APP_URL", "http://127.0.0.1:4000"));
        values.put("key", Env.get("APP_KEY", null));
        return values;
    }
}
