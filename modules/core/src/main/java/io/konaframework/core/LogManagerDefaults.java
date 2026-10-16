package io.konaframework.core;

/** Seeds default logging channel config (Java keeps Kover epilogue clean). */
final class LogManagerDefaults {
    private LogManagerDefaults() {}

    static Config seed(Config config) {
        if (!config.has("logging.default")) {
            config.set("logging.default", Env.getOrDefault("LOG_CHANNEL", "stack"));
        }
        seedChannel(config, "stack", "stack");
        if (!config.has("logging.channels.stack.channels")) {
            config.set("logging.channels.stack.channels", Env.getOrDefault("LOG_STACK", "single"));
        }
        seedChannel(config, "single", "single");
        if (!config.has("logging.channels.single.path")) {
            String path = config.getString("logging.file");
            config.set(
                "logging.channels.single.path",
                path != null ? path : FileLogging.DEFAULT_FILE_NAME
            );
        }
        seedChannel(config, "daily", "daily");
        if (!config.has("logging.channels.daily.path")) {
            String path = config.getString("logging.file");
            config.set(
                "logging.channels.daily.path",
                path != null ? path : FileLogging.DEFAULT_FILE_NAME
            );
        }
        if (!config.has("logging.channels.daily.days")) {
            String envRaw = Env.get("LOG_DAILY_DAYS", null);
            int days = 14;
            if (envRaw != null) {
                try {
                    days = Integer.parseInt(envRaw.trim());
                } catch (NumberFormatException ignored) {
                    days = 14;
                }
            }
            config.set("logging.channels.daily.days", days);
        }
        if (!config.has("logging.channels.emergency.path")) {
            String path = config.getString("logging.file");
            config.set(
                "logging.channels.emergency.path",
                path != null ? path : FileLogging.DEFAULT_FILE_NAME
            );
        }
        seedChannel(config, "stderr", "stderr");
        seedChannel(config, "stdout", "stdout");
        seedChannel(config, "console", "console");
        seedChannel(config, "syslog", "syslog");
        seedChannel(config, "errorlog", "errorlog");
        seedChannel(config, "null", "null");
        return config;
    }

    private static void seedChannel(Config config, String name, String driver) {
        String key = "logging.channels." + name + ".driver";
        if (!config.has(key)) {
            config.set(key, driver);
        }
    }
}
