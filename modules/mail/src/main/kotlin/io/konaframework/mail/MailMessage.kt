package io.konaframework.mail

class Address(
    val email: String,
    val name: String?,
) {
    constructor(email: String) : this(email, null)

    override fun toString(): String =
        if (name.isNullOrBlank()) email else "$name <$email>"
}

class MailMessage {
    var from: Address? = null
    var subject: String = ""
    var html: String? = null
    var text: String? = null

    private val toRecipients = mutableListOf<Address>()
    private val ccRecipients = mutableListOf<Address>()
    private val bccRecipients = mutableListOf<Address>()

    fun to(): List<Address> = toRecipients.toList()

    fun cc(): List<Address> = ccRecipients.toList()

    fun bcc(): List<Address> = bccRecipients.toList()

    fun to(email: String): MailMessage = to(email, null)

    fun to(email: String, name: String?): MailMessage {
        toRecipients += Address(email, name)
        return this
    }

    fun to(address: Address): MailMessage {
        toRecipients += address
        return this
    }

    fun cc(email: String): MailMessage = cc(email, null)

    fun cc(email: String, name: String?): MailMessage {
        ccRecipients += Address(email, name)
        return this
    }

    fun bcc(email: String): MailMessage = bcc(email, null)

    fun bcc(email: String, name: String?): MailMessage {
        bccRecipients += Address(email, name)
        return this
    }

    fun from(email: String): MailMessage = from(email, null)

    fun from(email: String, name: String?): MailMessage {
        from = Address(email, name)
        return this
    }

    fun subject(value: String): MailMessage {
        subject = value
        return this
    }

    fun html(value: String): MailMessage {
        html = value
        return this
    }

    fun text(value: String): MailMessage {
        text = value
        return this
    }
}
