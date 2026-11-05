package config;

import org.konaframework.core.ConfigFile;
import org.konaframework.core.Env;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Broadcast connections ({@code config/broadcasting.php} shape).
 *
 * <p>Supported drivers: {@code reverb}, {@code websocket}, {@code pusher}, {@code ably}, {@code log}, {@code null}.
 */
public final class Broadcasting implements ConfigFile {
    @Override
    public String key() {
        return "broadcasting";
    }

    @Override
    public Map<String, Object> config() {
        Map<String, Object> reverb = new LinkedHashMap<>();
        reverb.put("driver", "reverb");
        reverb.put("key", Env.get("REVERB_APP_KEY", null));
        reverb.put("secret", Env.get("REVERB_APP_SECRET", null));
        reverb.put("app_id", Env.get("REVERB_APP_ID", null));
        reverb.put("host", Env.getOrDefault("REVERB_HOST", "127.0.0.1"));
        reverb.put("port", envInt("REVERB_PORT", 8080));
        reverb.put("scheme", Env.getOrDefault("REVERB_SCHEME", "http"));

        Map<String, Object> websocket = new LinkedHashMap<>();
        websocket.put("driver", "websocket");
        websocket.put("key", Env.get("REVERB_APP_KEY", null));
        websocket.put("secret", Env.get("REVERB_APP_SECRET", null));
        websocket.put("app_id", Env.get("REVERB_APP_ID", null));
        websocket.put("host", Env.getOrDefault("REVERB_HOST", "127.0.0.1"));
        websocket.put("port", envInt("REVERB_PORT", 8080));
        websocket.put("scheme", Env.getOrDefault("REVERB_SCHEME", "http"));

        Map<String, Object> pusher = new LinkedHashMap<>();
        pusher.put("driver", "pusher");
        pusher.put("key", Env.get("PUSHER_APP_KEY", null));
        pusher.put("secret", Env.get("PUSHER_APP_SECRET", null));
        pusher.put("app_id", Env.get("PUSHER_APP_ID", null));
        pusher.put("host", Env.getOrDefault("PUSHER_HOST", "api-mt1.pusher.com"));
        pusher.put("port", envInt("PUSHER_PORT", 443));
        pusher.put("scheme", Env.getOrDefault("PUSHER_SCHEME", "https"));

        Map<String, Object> ably = new LinkedHashMap<>();
        ably.put("driver", "ably");
        ably.put("key", Env.get("ABLY_KEY", null));

        Map<String, Object> log = new LinkedHashMap<>();
        log.put("driver", "log");

        Map<String, Object> nullDriver = new LinkedHashMap<>();
        nullDriver.put("driver", "null");

        Map<String, Object> connections = new LinkedHashMap<>();
        connections.put("reverb", reverb);
        connections.put("websocket", websocket);
        connections.put("pusher", pusher);
        connections.put("ably", ably);
        connections.put("log", log);
        connections.put("null", nullDriver);

        Map<String, Object> reverbMeta = new LinkedHashMap<>();
        reverbMeta.put("scaling", Env.getOrDefault("REVERB_SCALING", "false"));
        reverbMeta.put("scaling_channel", Env.getOrDefault("REVERB_SCALING_CHANNEL", "kona:reverb"));

        Map<String, Object> values = new LinkedHashMap<>();
        /*
        |--------------------------------------------------------------------------
        | Default Broadcaster
        |--------------------------------------------------------------------------
        |
        | Supported: "reverb", "websocket", "pusher", "ably", "log", "null"
        |
        */
        values.put("default", Env.getOrDefault("BROADCAST_CONNECTION", "log"));
        values.put("reverb", reverbMeta);
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
