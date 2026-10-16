package io.konaframework.mail

/**
 * Laravel-style mailable. Override [build] or the envelope/content helpers.
 */
abstract class Mailable {
    open fun envelope(): Envelope = Envelope()

    open fun content(): Content = Content()

    open fun build(): MailMessage {
        val env = envelope()
        val body = content()
        val message = MailMessage()
            .subject(env.subject)
        env.from?.let { message.from(it.email, it.name) }
        env.to.forEach { message.to(it) }
        env.cc.forEach { message.cc(it.email, it.name) }
        env.bcc.forEach { message.bcc(it.email, it.name) }
        body.html?.let { message.html(it) }
        body.text?.let { message.text(it) }
        return message
    }
}

class Envelope {
    val subject: String
    val from: Address?
    val to: List<Address>
    val cc: List<Address>
    val bcc: List<Address>

    @JvmOverloads
    constructor(
        subject: String = "",
        from: Address? = null,
        to: List<Address> = emptyList(),
        cc: List<Address> = emptyList(),
        bcc: List<Address> = emptyList(),
    ) {
        this.subject = subject
        this.from = from
        this.to = to
        this.cc = cc
        this.bcc = bcc
    }
}

class Content {
    val html: String?
    val text: String?

    @JvmOverloads
    constructor(html: String? = null, text: String? = null) {
        this.html = html
        this.text = text
    }
}
