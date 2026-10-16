package config;

import io.konaframework.core.ConfigFile;
import java.util.Map;

public final class Paths implements ConfigFile {
    @Override
    public String key() {
        return "paths";
    }

    @Override
    public Map<String, Object> config() {
        return Map.of(
            "langRoot", "src/main/java",
            "resources", "src/main/resources",
            "storage", "storage",
            "public", "src/main/resources/public"
        );
    }
}
