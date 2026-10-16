package config;

import io.konaframework.core.ConfigFile;
import io.konaframework.core.Env;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Filesystems implements ConfigFile {
    @Override
    public String key() {
        return "filesystems";
    }

    @Override
    public Map<String, Object> config() {
        Map<String, Object> local = new LinkedHashMap<>();
        local.put("driver", "local");
        local.put("root", "storage/app");

        Map<String, Object> pub = new LinkedHashMap<>();
        pub.put("driver", "local");
        pub.put("root", "storage/app/public");
        pub.put("url", "/storage");

        Map<String, Object> disks = new LinkedHashMap<>();
        disks.put("local", local);
        disks.put("public", pub);

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("default", Env.getOrDefault("FILESYSTEM_DISK", "local"));
        values.put("disks", disks);
        return values;
    }
}
