package config;

import org.konaframework.core.ConfigFile;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Project layout paths ({@code paths.*}).
 *
 * <p>These tell grind and the HTTP kernel where source, views, storage, and the
 * public web root live relative to the application base path.
 */
public final class Paths implements ConfigFile {
    @Override
    public String key() {
        return "paths";
    }

    @Override
    public Map<String, Object> config() {
        Map<String, Object> values = new LinkedHashMap<>();
        /*
        |--------------------------------------------------------------------------
        | Language / source root
        |--------------------------------------------------------------------------
        */
        values.put("langRoot", "src/main/java");

        /*
        |--------------------------------------------------------------------------
        | Resources
        |--------------------------------------------------------------------------
        */
        values.put("resources", "src/main/resources");

        /*
        |--------------------------------------------------------------------------
        | Storage
        |--------------------------------------------------------------------------
        */
        values.put("storage", "storage");

        /*
        |--------------------------------------------------------------------------
        | Public directory
        |--------------------------------------------------------------------------
        */
        values.put("public", "public");
        return values;
    }
}
