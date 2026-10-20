package config;

import org.konaframework.core.ConfigFile;
import org.konaframework.core.Env;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Filesystem disks ({@code config/filesystems.php} shape).
 *
 * <p>{@code default} selects the disk used by Storage. {@code links} is the map
 * {@code ./grind storage:link} creates (public URL prefix → on-disk root).
 */
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
        local.put("throw", false);

        Map<String, Object> pub = new LinkedHashMap<>();
        pub.put("driver", "local");
        pub.put("root", "storage/app/public");
        pub.put("url", "/storage");
        pub.put("visibility", "public");
        pub.put("throw", false);

        Map<String, Object> s3 = new LinkedHashMap<>();
        s3.put("driver", "s3");
        s3.put("key", Env.get("AWS_ACCESS_KEY_ID", null));
        s3.put("secret", Env.get("AWS_SECRET_ACCESS_KEY", null));
        s3.put("region", Env.getOrDefault("AWS_DEFAULT_REGION", "us-east-1"));
        s3.put("bucket", Env.get("AWS_BUCKET", null));
        s3.put("url", Env.get("AWS_URL", null));
        s3.put("endpoint", Env.get("AWS_ENDPOINT", null));
        s3.put("prefix", Env.getOrDefault("AWS_PREFIX", ""));
        s3.put("throw", false);

        Map<String, Object> memory = new LinkedHashMap<>();
        memory.put("driver", "memory");
        memory.put("bucket", Env.getOrDefault("FILESYSTEM_MEMORY_BUCKET", "kona"));

        Map<String, Object> disks = new LinkedHashMap<>();
        disks.put("local", local);
        disks.put("public", pub);
        disks.put("s3", s3);
        disks.put("memory", memory);

        Map<String, Object> links = new LinkedHashMap<>();
        links.put("public/storage", "storage/app/public");

        Map<String, Object> values = new LinkedHashMap<>();
        /*
        |--------------------------------------------------------------------------
        | Default Filesystem Disk
        |--------------------------------------------------------------------------
        |
        | Here you may specify the default filesystem disk that should be used
        | by the framework. The "local" disk, as well as a public disk that
        | is meant to be web-accessible, are configured below.
        |
        */
        values.put("default", Env.getOrDefault("FILESYSTEM_DISK", "local"));

        /*
        |--------------------------------------------------------------------------
        | Filesystem Disks
        |--------------------------------------------------------------------------
        |
        | Below you may configure as many filesystem disks as necessary.
        | Supported drivers: "local", "s3", "memory"
        |
        */
        values.put("disks", disks);

        /*
        |--------------------------------------------------------------------------
        | Symbolic Links
        |--------------------------------------------------------------------------
        |
        | Here you may configure the symbolic links that will be created when the
        | `storage:link` grind command is executed.
        |
        */
        values.put("links", links);
        return values;
    }
}
