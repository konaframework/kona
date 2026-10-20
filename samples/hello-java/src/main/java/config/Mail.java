package config;

import org.konaframework.core.ConfigFile;
import org.konaframework.core.Env;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mailers ({@code config/mail.php} shape).
 *
 * <p>Supported transports: {@code log} / {@code array}, {@code smtp}.
 */
public final class Mail implements ConfigFile {
    @Override
    public String key() {
        return "mail";
    }

    @Override
    public Map<String, Object> config() {
        Map<String, Object> smtp = new LinkedHashMap<>();
        smtp.put("transport", "smtp");
        smtp.put("host", Env.getOrDefault("MAIL_HOST", "127.0.0.1"));
        smtp.put("port", envInt("MAIL_PORT", 587));
        smtp.put("username", Env.get("MAIL_USERNAME", null));
        smtp.put("password", Env.get("MAIL_PASSWORD", null));

        Map<String, Object> log = new LinkedHashMap<>();
        log.put("transport", "log");
        log.put("channel", Env.get("MAIL_LOG_CHANNEL", null));

        Map<String, Object> array = new LinkedHashMap<>();
        array.put("transport", "array");

        Map<String, Object> mailers = new LinkedHashMap<>();
        mailers.put("smtp", smtp);
        mailers.put("log", log);
        mailers.put("array", array);

        Map<String, Object> from = new LinkedHashMap<>();
        from.put("address", Env.getOrDefault("MAIL_FROM_ADDRESS", "hello@example.com"));
        from.put("name", Env.getOrDefault("MAIL_FROM_NAME", Env.getOrDefault("APP_NAME", "hello-java")));

        Map<String, Object> values = new LinkedHashMap<>();
        /*
        |--------------------------------------------------------------------------
        | Default Mailer
        |--------------------------------------------------------------------------
        |
        | This option controls the default mailer that is used to send all email
        | messages unless another mailer is explicitly specified when sending
        | the message. All additional mailers can be configured below.
        |
        */
        values.put("default", Env.getOrDefault("MAIL_MAILER", "log"));

        /*
        |--------------------------------------------------------------------------
        | Mailer Configurations
        |--------------------------------------------------------------------------
        |
        | Here you may configure all of the mailers used by your application plus
        | their respective settings. The `log` transport writes messages through
        | the application logger (handy in local). Switch to `smtp` to deliver.
        |
        | Supported: "smtp", "log", "array"
        |
        */
        values.put("mailers", mailers);

        /*
        |--------------------------------------------------------------------------
        | Global "From" Address
        |--------------------------------------------------------------------------
        |
        | You may wish for all emails sent by your application to be sent from
        | the same address. Here you may specify a name and address that is
        | used globally for all emails that are sent by your application.
        |
        */
        values.put("from", from);
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
