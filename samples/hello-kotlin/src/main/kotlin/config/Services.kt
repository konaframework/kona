package config

import org.konaframework.core.ConfigFile
import org.konaframework.core.Env

/**
 * Third-party service credentials (`config/services.php` shape).
 *
 * These keys are not consumed by the core kernel. Application code and
 * packages read them via `config("services.ses.key")` (and similar).
 */
object Services : ConfigFile {
    override fun key(): String = "services"

    override fun config(): Map<String, Any?> = mapOf(
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
        "postmark" to mapOf(
            "token" to Env.get("POSTMARK_TOKEN"),
        ),
        "resend" to mapOf(
            "key" to Env.get("RESEND_KEY"),
        ),
        "ses" to mapOf(
            "key" to Env.get("AWS_ACCESS_KEY_ID"),
            "secret" to Env.get("AWS_SECRET_ACCESS_KEY"),
            "region" to Env.getOrDefault("AWS_DEFAULT_REGION", "us-east-1"),
        ),
        "slack" to mapOf(
            "notifications" to mapOf(
                "bot_user_oauth_token" to Env.get("SLACK_BOT_USER_OAUTH_TOKEN"),
                "channel" to Env.get("SLACK_BOT_USER_DEFAULT_CHANNEL"),
            ),
        ),
    )
}
