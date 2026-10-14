package dev.kona.mail

import dev.kona.core.Config
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves named mailers from config.
 *
 * - `mail.default` — mailer name (default `log`)
 * - `mail.mailers.{name}.transport` — `log` / `array`, `smtp`
 * - `mail.from.address` / `mail.from.name`
 * - SMTP: `mail.mailers.{name}.host`, `port`, `username`, `password`
 */
class MailManager(
    private val config: Config,
) {
    private val mailers = ConcurrentHashMap<String, Mailer>()

    fun mailer(): Mailer = mailer(defaultMailer())

    fun mailer(name: String): Mailer =
        mailers.getOrPut(name) { create(name) }

    fun defaultMailer(): String = config.getString("mail.default", "log")

    private fun create(name: String): Mailer {
        val configured = config.getString("mail.mailers.$name.transport")
        val transportName = when {
            configured != null -> configured
            name.equals("log", true) || name.equals("array", true) || name.equals("smtp", true) -> name
            else -> "log"
        }
        val transport = when (transportName.lowercase()) {
            "log", "array" -> LogTransport()
            "smtp" -> SmtpTransport(
                host = config.getString("mail.mailers.$name.host", "127.0.0.1"),
                port = config.getInt("mail.mailers.$name.port", 25),
                username = config.getString("mail.mailers.$name.username"),
                password = config.getString("mail.mailers.$name.password"),
            )
            else -> throw IllegalArgumentException("Mailer [$name] is not defined.")
        }
        val fromAddress = config.getString("mail.from.address")
        val from = fromAddress?.let { Address(it, config.getString("mail.from.name")) }
        return Mailer(transport, from)
    }
}
