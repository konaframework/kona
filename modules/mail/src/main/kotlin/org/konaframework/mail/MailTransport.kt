package org.konaframework.mail

import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList

fun interface MailTransport {
    fun send(message: MailMessage)
}

/**
 * Records messages and logs them (Laravel `log` mailer). Ideal for tests.
 */
class LogTransport : MailTransport {
    private val log = LoggerFactory.getLogger(LogTransport::class.java)
    private val sent = CopyOnWriteArrayList<MailMessage>()

    fun messages(): List<MailMessage> = sent.toList()

    fun clear() {
        sent.clear()
    }

    override fun send(message: MailMessage) {
        sent += message
        log.info(
            "Mail to={} subject={} text={} html={}",
            message.to().joinToString { it.email },
            message.subject,
            message.text,
            message.html,
        )
    }
}

/**
 * Minimal SMTP client (HELO / MAIL / RCPT / DATA, optional AUTH LOGIN).
 */
class SmtpTransport @JvmOverloads constructor(
    private val host: String,
    private val port: Int = 25,
    private val username: String? = null,
    private val password: String? = null,
    private val ehloName: String = "localhost",
    private val socketFactory: (String, Int) -> Socket = { h, p -> Socket(h, p) },
) : MailTransport {
    override fun send(message: MailMessage) {
        socketFactory(host, port).use { socket ->
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))
            expect(reader, 220)
            command(writer, reader, "EHLO $ehloName", 250)
            if (!username.isNullOrBlank() && password != null) {
                command(writer, reader, "AUTH LOGIN", 334)
                command(writer, reader, Base64.getEncoder().encodeToString(username.toByteArray(StandardCharsets.UTF_8)), 334)
                command(writer, reader, Base64.getEncoder().encodeToString(password.toByteArray(StandardCharsets.UTF_8)), 235)
            }
            val from = message.from?.email ?: throw IllegalStateException("Mail message requires a from address")
            command(writer, reader, "MAIL FROM:<$from>", 250)
            val recipients = message.to() + message.cc() + message.bcc()
            if (recipients.isEmpty()) throw IllegalStateException("Mail message requires recipients")
            recipients.forEach { command(writer, reader, "RCPT TO:<${it.email}>", 250) }
            command(writer, reader, "DATA", 354)
            writer.write(buildData(message))
            writer.write("\r\n.\r\n")
            writer.flush()
            expect(reader, 250)
            command(writer, reader, "QUIT", 221)
        }
    }

    private fun buildData(message: MailMessage): String {
        val sb = StringBuilder()
        message.from?.let { sb.append("From: ").append(it).append("\r\n") }
        sb.append("To: ").append(message.to().joinToString { it.toString() }).append("\r\n")
        if (message.cc().isNotEmpty()) {
            sb.append("Cc: ").append(message.cc().joinToString { it.toString() }).append("\r\n")
        }
        sb.append("Subject: ").append(message.subject).append("\r\n")
        if (message.html != null) {
            sb.append("MIME-Version: 1.0\r\n")
            sb.append("Content-Type: text/html; charset=utf-8\r\n\r\n")
            sb.append(message.html)
        } else {
            sb.append("Content-Type: text/plain; charset=utf-8\r\n\r\n")
            sb.append(message.text ?: "")
        }
        return sb.toString().replace("\r\n.", "\r\n..")
    }

    private fun command(writer: BufferedWriter, reader: BufferedReader, line: String, expectCode: Int) {
        writer.write(line)
        writer.write("\r\n")
        writer.flush()
        expect(reader, expectCode)
    }

    private fun expect(reader: BufferedReader, code: Int) {
        val line = reader.readLine() ?: throw IllegalStateException("SMTP server closed connection")
        val actual = line.take(3).toIntOrNull()
            ?: throw IllegalStateException("Unexpected SMTP response: $line")
        if (actual != code) {
            throw IllegalStateException("Expected SMTP $code but got: $line")
        }
        // consume multi-line 250- replies
        if (line.length > 3 && line[3] == '-') {
            while (true) {
                val next = reader.readLine() ?: break
                if (next.length > 3 && next[3] == ' ') break
                if (next.length <= 3) break
            }
        }
    }
}
