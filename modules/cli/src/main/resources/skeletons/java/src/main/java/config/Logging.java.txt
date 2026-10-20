package config;

import org.konaframework.core.Config;
import org.konaframework.core.ConfigFile;
import org.konaframework.core.Env;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Log channels ({@code config/logging.php} shape).
 *
 * <p>Supported drivers: {@code stack}, {@code single}, {@code daily}, {@code stderr},
 * {@code stdout}, {@code console}, {@code syslog}, {@code errorlog}, {@code null}.
 */
public final class Logging implements ConfigFile {
    @Override
    public String key() {
        return "logging";
    }

    @Override
    public Map<String, Object> config() {
        String level = Env.getOrDefault("LOG_LEVEL", "debug");
        String path = Env.getOrDefault("LOG_FILE", "storage/logs/kona.log");

        Map<String, Object> stack = new LinkedHashMap<>();
        stack.put("driver", "stack");
        stack.put("channels", Env.getOrDefault("LOG_STACK", "single"));
        stack.put("ignore_exceptions", false);

        Map<String, Object> single = new LinkedHashMap<>();
        single.put("driver", "single");
        single.put("path", path);
        single.put("level", level);

        Map<String, Object> daily = new LinkedHashMap<>();
        daily.put("driver", "daily");
        daily.put("path", path);
        daily.put("level", level);
        daily.put("days", envInt("LOG_DAILY_DAYS", 14));

        Map<String, Object> stderr = channel("stderr", level);
        Map<String, Object> stdout = channel("stdout", level);
        Map<String, Object> console = channel("console", level);

        Map<String, Object> syslog = new LinkedHashMap<>();
        syslog.put("driver", "syslog");
        syslog.put("level", level);
        syslog.put("host", Env.getOrDefault("LOG_SYSLOG_HOST", "localhost"));
        syslog.put("facility", Env.getOrDefault("LOG_SYSLOG_FACILITY", "USER"));

        Map<String, Object> errorlog = channel("errorlog", level);

        Map<String, Object> nullChannel = new LinkedHashMap<>();
        nullChannel.put("driver", "null");

        Map<String, Object> emergency = new LinkedHashMap<>();
        emergency.put("path", path);

        Map<String, Object> channels = new LinkedHashMap<>();
        channels.put("stack", stack);
        channels.put("single", single);
        channels.put("daily", daily);
        channels.put("stderr", stderr);
        channels.put("stdout", stdout);
        channels.put("console", console);
        channels.put("syslog", syslog);
        channels.put("errorlog", errorlog);
        channels.put("null", nullChannel);
        channels.put("emergency", emergency);

        Map<String, Object> deprecations = new LinkedHashMap<>();
        deprecations.put("channel", Env.getOrDefault("LOG_DEPRECATIONS_CHANNEL", "null"));
        deprecations.put("trace", false);

        Map<String, Object> values = new LinkedHashMap<>();
        /*
        |--------------------------------------------------------------------------
        | Default Log Channel
        |--------------------------------------------------------------------------
        |
        | This option defines the default log channel that is utilized to write
        | messages to your logs. The value provided here should match one of
        | the channels present in the list of "channels" configured below.
        |
        */
        values.put("default", Env.getOrDefault("LOG_CHANNEL", "stack"));
        values.put("file", path);
        values.put("level", level);
        values.put("to_file", Boolean.TRUE.equals(Config.toBoolean(Env.getOrDefault("LOG_TO_FILE", "true"))));

        /*
        |--------------------------------------------------------------------------
        | Deprecations Log Channel
        |--------------------------------------------------------------------------
        |
        | This option controls the log channel that should be used to log
        | warnings regarding deprecated library features.
        |
        */
        values.put("deprecations", deprecations);

        /*
        |--------------------------------------------------------------------------
        | Log Channels
        |--------------------------------------------------------------------------
        |
        | Here you may configure the log channels for your application.
        |
        | Available drivers: "stack", "single", "daily", "stderr", "stdout",
        |                    "console", "syslog", "errorlog", "null"
        |
        */
        values.put("channels", channels);
        return values;
    }

    private static Map<String, Object> channel(String driver, String level) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("driver", driver);
        map.put("level", level);
        return map;
    }

    private static int envInt(String key, int fallback) {
        try {
            return Integer.parseInt(Env.getOrDefault(key, Integer.toString(fallback)).trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
