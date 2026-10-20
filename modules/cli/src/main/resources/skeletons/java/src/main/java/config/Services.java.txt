package config;

import org.konaframework.core.ConfigFile;
import org.konaframework.core.Env;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Third-party service credentials ({@code config/services.php} shape).
 *
 * <p>These keys are not consumed by the core kernel. Application code and
 * packages read them via {@code config("services.ses.key")} (and similar).
 */
public final class Services implements ConfigFile {
    @Override
    public String key() {
        return "services";
    }

    @Override
    public Map<String, Object> config() {
        Map<String, Object> postmark = new LinkedHashMap<>();
        postmark.put("token", Env.get("POSTMARK_TOKEN", null));

        Map<String, Object> resend = new LinkedHashMap<>();
        resend.put("key", Env.get("RESEND_KEY", null));

        Map<String, Object> ses = new LinkedHashMap<>();
        ses.put("key", Env.get("AWS_ACCESS_KEY_ID", null));
        ses.put("secret", Env.get("AWS_SECRET_ACCESS_KEY", null));
        ses.put("region", Env.getOrDefault("AWS_DEFAULT_REGION", "us-east-1"));

        Map<String, Object> slackNotifications = new LinkedHashMap<>();
        slackNotifications.put("bot_user_oauth_token", Env.get("SLACK_BOT_USER_OAUTH_TOKEN", null));
        slackNotifications.put("channel", Env.get("SLACK_BOT_USER_DEFAULT_CHANNEL", null));

        Map<String, Object> slack = new LinkedHashMap<>();
        slack.put("notifications", slackNotifications);

        Map<String, Object> values = new LinkedHashMap<>();
        /*
        |--------------------------------------------------------------------------
        | Third Party Services
        |--------------------------------------------------------------------------
        |
        | This file is for storing the credentials for third party services such
        | as Mailgun, Postmark, AWS, and more. This file provides the de facto
        | location for this type of information, allowing packages to have
        | a conventional file to locate the various service credentials.
        |
        */
        values.put("postmark", postmark);
        values.put("resend", resend);
        values.put("ses", ses);
        values.put("slack", slack);
        return values;
    }
}
