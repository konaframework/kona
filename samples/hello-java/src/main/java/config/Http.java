package config;

import dev.kona.core.ConfigFile;
import java.util.List;
import java.util.Map;

public final class Http implements ConfigFile {
    @Override
    public String key() {
        return "http";
    }

    @Override
    public Map<String, Object> config() {
        return Map.of(
            "trusted_proxies", List.of("*"),
            "trusted_hosts", List.of()
        );
    }
}
