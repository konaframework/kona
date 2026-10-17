package org.konaframework.mail

/**
 * Sends [MailMessage]s / [Mailable]s through a [MailTransport].
 */
class Mailer(
    private val transport: MailTransport,
    private val defaultFrom: Address?,
) {
    fun send(message: MailMessage) {
        if (message.from == null && defaultFrom != null) {
            message.from = defaultFrom
        }
        transport.send(message)
    }

    fun send(mailable: Mailable) {
        send(mailable.build())
    }

    fun to(email: String): PendingMail = to(email, null)

    fun to(email: String, name: String?): PendingMail =
        PendingMail(this, listOf(Address(email, name)))

    fun toAddresses(vararg addresses: Address): PendingMail =
        PendingMail(this, addresses.toList())
}

class PendingMail(
    private val mailer: Mailer,
    private val to: List<Address>,
) {
    fun send(mailable: Mailable) {
        val message = mailable.build()
        to.forEach { message.to(it) }
        mailer.send(message)
    }

    fun send(message: MailMessage) {
        to.forEach { message.to(it) }
        mailer.send(message)
    }
}
