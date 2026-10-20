package config;

import org.konaframework.core.ConfigFile;
import org.konaframework.core.Env;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Broadcast connections ({@code config/broadcasting.php} shape).
 *
 * <p>Supported drivers: {@code log}, {@code null}, {@code websocket}.
 */
public final class Broadcasting implements ConfigFile {
    @Override
    public String key() {
        return "broadcasting";
    }

    @Override
    public Map<String, Object> config() {
        Map<String, Object> websocket = new LinkedHashMap<>();
        websocket.put("driver", "websocket");
        websocket.put("key", Env.get("REVERB_APP_KEY", null));
        websocket.put("secret", Env.get("REVERB_APP_SECRET", null));
        websocket.put("app_id", Env.get("REVERB_APP_ID", null));
        websocket.put("host", Env.getOrDefault("REVERB_HOST", "127.0.0.1"));
        websocket.put("port", envInt("REVERB_PORT", 8080));
        websocket.put("scheme", Env.getOrDefault("REVERB_SCHEME", "http"));

        Map<String, Object> log = new LinkedHashMap<>();
        log.put("driver", "log");

        Map<String, Object> nullDriver = new LinkedHashMap<>();
        nullDriver.put("driver", "null");

        Map<String, Object> connections = new LinkedHashMap<>();
        connections.put("websocket", websocket);
        connections.put("log", log);
        connections.put("null", nullDriver);

        Map<String, Object> values = new LinkedHashMap<>();
        /*
        |--------------------------------------------------------------------------
        | Default Broadcaster
        |--------------------------------------------------------------------------
        |
        | This option controls the default broadcaster that will be used by the
        | framework when an event needs to be broadcast. You may set this to
        | any of the connections defined in the "connections" array below.
        |
        | Supported: "websocket", "log", "null"
        |
        */
        values.put("default", Env.getOrDefault("BROADCAST_CONNECTION", "log"));

        /*
        |--------------------------------------------------------------------------
        | Broadcast Connections
        |--------------------------------------------------------------------------
        |
        | Here you may define all of the broadcast connections that will be used
        | to broadcast events to other systems or over WebSockets. Samples of
        | each available type of connection are provided inside this array.
        |
        */
        values.put("connections", connections);
        return values;
    }

    private static int envInt(String key, int fallback) {
        try {
            return Integer.parseInt(Env.getOrDefault(key, Integer.toString(fallback)).trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
