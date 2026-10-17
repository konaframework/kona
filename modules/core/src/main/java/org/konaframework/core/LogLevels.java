package org.konaframework.core;

/** Java-friendly log level parsing (keeps Kotlin enum coverage clean). */
final class LogLevels {
    private LogLevels() {}

    static LogLevel parse(String raw) {
        String key = raw.trim().toLowerCase();
        switch (key) {
            case "emergency":
            case "emerg":
                return LogLevel.EMERGENCY;
            case "alert":
                return LogLevel.ALERT;
            case "critical":
            case "crit":
                return LogLevel.CRITICAL;
            case "error":
            case "err":
                return LogLevel.ERROR;
            case "warning":
            case "warn":
                return LogLevel.WARNING;
            case "notice":
                return LogLevel.NOTICE;
            case "info":
                return LogLevel.INFO;
            case "debug":
                return LogLevel.DEBUG;
            default:
                throw new IllegalArgumentException("Unknown log level [" + raw + "]");
        }
    }
}
