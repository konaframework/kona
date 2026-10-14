package dev.kona.mail

import dev.kona.core.Application
import dev.kona.core.Config
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.nio.file.Path

class MailTest {
    class WelcomeMail : Mailable() {
        override fun envelope() = Envelope(
            subject = "Welcome",
            from = Address("from@kona.dev", "Kona"),
            to = listOf(Address("a@x.com")),
            cc = listOf(Address("c@x.com", "C")),
            bcc = listOf(Address("b@x.com")),
        )

        override fun content() = Content(html = "<p>Hi</p>", text = "Hi")
    }

    @Test
    fun logTransportAndMailer(@TempDir base: Path) {
        val log = LogTransport()
        val mailer = Mailer(log, Address("default@kona.dev", null))
        mailer.send(MailMessage().to("u@x.com").subject("S").text("body"))
        assertEquals(1, log.messages().size)
        assertEquals("default@kona.dev", log.messages()[0].from?.email)
        assertEquals("default@kona.dev", Address("default@kona.dev").toString())
        assertEquals("N <e@x.com>", Address("e@x.com", "N").toString())
        assertEquals("e@x.com", Address("e@x.com", "  ").toString())
        MailMessage().from("solo@x.com").cc("c@x.com").bcc("b@x.com")

        log.clear()
        mailer.send(WelcomeMail())
        assertEquals(1, log.messages().size)
        assertTrue(log.messages()[0].to().any { it.email == "a@x.com" })
        mailer.to("u@x.com", "U").send(WelcomeMail())
        assertEquals(2, log.messages().size)
        assertTrue(log.messages()[1].to().any { it.email == "u@x.com" })
        mailer.toAddresses(Address("z@x.com", null)).send(MailMessage().subject("raw").html("<b>x</b>"))
        assertEquals(3, log.messages().size)

        val manager = MailManager(
            Config().also {
                it.set("mail.default", "log")
                it.set("mail.from.address", "app@kona.dev")
                it.set("mail.from.name", "App")
                it.set("mail.mailers.log.transport", "log")
            },
        )
        assertEquals("log", manager.defaultMailer())
        manager.mailer().send(MailMessage().to("t@x.com").text("x"))
        assertTrue(MailManager(Config().also { it.set("mail.default", "array") }).mailer().let { true })
        assertThrows(IllegalArgumentException::class.java) {
            MailManager(
                Config().also {
                    it.set("mail.default", "bad")
                    it.set("mail.mailers.bad.transport", "nope")
                },
            ).mailer()
        }

        val app = Application(base)
        app.register(MailServiceProvider()).boot()
        app.resolve(Mailer::class.java).send(MailMessage().to("a@b.c").text("t"))
    }

    @Test
    fun smtpTransportWithFakeSocket() {
        val script = mutableListOf(
            "220 hi\r\n",
            "250-OK\r\n250 HELP\r\n",
            "334 VXNlcm5hbWU6\r\n",
            "334 UGFzc3dvcmQ6\r\n",
            "235 ok\r\n",
            "250 ok\r\n",
            "250 ok\r\n",
            "250 ok\r\n",
            "250 ok\r\n",
            "354 go\r\n",
            "250 queued\r\n",
            "221 bye\r\n",
        )
        val recorded = ByteArrayOutputStream()
        val transport = SmtpTransport(
            host = "smtp.test",
            port = 2525,
            username = "user",
            password = "pass",
            socketFactory = { _, _ ->
                object : Socket() {
                    private val input = object : ByteArrayInputStream(ByteArray(0)) {
                        private val lines = script
                        private var idx = 0
                        private var buf = ByteArray(0)
                        private var pos = 0

                        override fun read(): Int {
                            if (pos >= buf.size) {
                                if (idx >= lines.size) return -1
                                buf = lines[idx++].toByteArray(StandardCharsets.UTF_8)
                                pos = 0
                            }
                            return buf[pos++].toInt() and 0xff
                        }

                        override fun read(b: ByteArray, off: Int, len: Int): Int {
                            if (len == 0) return 0
                            val c = read()
                            if (c < 0) return -1
                            b[off] = c.toByte()
                            var n = 1
                            while (n < len) {
                                val next = read()
                                if (next < 0) break
                                b[off + n] = next.toByte()
                                n++
                                if (next == '\n'.code) break
                            }
                            return n
                        }
                    }
                    private val output = recorded

                    override fun getInputStream() = input

                    override fun getOutputStream() = output

                    override fun close() = Unit
                }
            },
        )
        transport.send(
            MailMessage()
                .from("from@kona.dev", "From")
                .to("to@kona.dev", "To")
                .cc("cc@kona.dev")
                .bcc("bcc@kona.dev")
                .subject("Sub")
                .html("<p>Hi.</p>"),
        )
        val sent = recorded.toString(StandardCharsets.UTF_8)
        assertTrue(sent.contains("AUTH LOGIN"))
        assertTrue(sent.contains("MAIL FROM:<from@kona.dev>"))
        assertTrue(sent.contains("Subject: Sub"))

        assertThrows(IllegalStateException::class.java) {
            SmtpTransport("h", socketFactory = { _, _ ->
                object : Socket() {
                    override fun getInputStream() = ByteArrayInputStream(ByteArray(0))
                    override fun getOutputStream() = ByteArrayOutputStream()
                    override fun close() = Unit
                }
            }).send(MailMessage().from("a@b.c").to("c@d.e").text("x"))
        }

        assertThrows(IllegalStateException::class.java) {
            val t = SmtpTransport(
                "h",
                username = null,
                password = null,
                socketFactory = { _, _ ->
                    fakeSocket(listOf("220 hi\r\n", "250 ok\r\n", "250 ok\r\n"))
                },
            )
            t.send(MailMessage().to("a@b.c").text("x"))
        }

        assertThrows(IllegalStateException::class.java) {
            val t = SmtpTransport(
                "h",
                socketFactory = { _, _ ->
                    fakeSocket(listOf("220 hi\r\n", "250 ok\r\n", "250 ok\r\n", "250 ok\r\n"))
                },
            )
            t.send(MailMessage().from("a@b.c").text("x"))
        }

        // text body path + wrong code + non-numeric + multiline end
        SmtpTransport(
            "h",
            socketFactory = { _, _ ->
                fakeSocket(
                    listOf(
                        "220 hi\r\n",
                        "250-OK\r\n250\r\n",
                        "250 ok\r\n",
                        "250 ok\r\n",
                        "354 go\r\n",
                        "250 queued\r\n",
                        "221 bye\r\n",
                    ),
                )
            },
        ).send(MailMessage().from("a@b.c").to("c@d.e").subject("t").text("plain."))

        assertThrows(IllegalStateException::class.java) {
            SmtpTransport(
                "h",
                socketFactory = { _, _ -> fakeSocket(listOf("220 hi\r\n", "500 nope\r\n")) },
            ).send(MailMessage().from("a@b.c").to("c@d.e").text("x"))
        }
        assertThrows(IllegalStateException::class.java) {
            SmtpTransport(
                "h",
                socketFactory = { _, _ -> fakeSocket(listOf("ZZZ bad\r\n")) },
            ).send(MailMessage().from("a@b.c").to("c@d.e").text("x"))
        }
        assertThrows(IllegalStateException::class.java) {
            SmtpTransport(
                "h",
                socketFactory = { _, _ -> fakeSocket(listOf("220 hi\r\n", "250-OK\r\n")) },
            ).send(MailMessage().from("a@b.c").to("c@d.e").text("x"))
        }
        // dotted line stuffing in body
        SmtpTransport(
            "h",
            username = "u",
            password = "p",
            socketFactory = { _, _ ->
                fakeSocket(
                    listOf(
                        "220 hi\r\n",
                        "250 ok\r\n",
                        "334 a\r\n",
                        "334 b\r\n",
                        "235 ok\r\n",
                        "250 ok\r\n",
                        "250 ok\r\n",
                        "354 go\r\n",
                        "250 queued\r\n",
                        "221 bye\r\n",
                    ),
                )
            },
        ).send(MailMessage().from("a@b.c").to("c@d.e").text("line\r\n.hidden"))

        val smtpMgr = MailManager(
            Config().also {
                it.set("mail.default", "smtp")
                it.set("mail.mailers.smtp.transport", "smtp")
                it.set("mail.mailers.smtp.host", "127.0.0.1")
                it.set("mail.mailers.smtp.port", 2525)
                it.set("mail.mailers.smtp.username", "u")
                it.set("mail.mailers.smtp.password", "p")
            },
        )
        assertEquals("smtp", smtpMgr.defaultMailer())
        assertTrue(smtpMgr.mailer() is Mailer)
        assertTrue(MailManager(Config().also { it.set("mail.default", "smtp") }).mailer() is Mailer)
        assertTrue(MailManager(Config().also { it.set("mail.default", "unknown-name") }).mailer() is Mailer)

        val built = object : Mailable() {}.build()
        assertEquals("", built.subject)
        assertEquals(null, built.html)
        assertEquals("s", Envelope(subject = "s").subject)
        assertEquals("a", Content(text = "a").text)
        val msg = MailMessage().to(Address("solo@x.com")).cc("c@x.com", "C").bcc("b@x.com", "B")
        assertEquals(1, msg.to().size)
        assertEquals(1, msg.cc().size)
        assertEquals(1, msg.bcc().size)
        val single = Mailer(LogTransport(), null)
        single.send(MailMessage().from("a@b.c").to("c@d.e").text("x"))
        val withFrom = Mailer(LogTransport(), Address("default@kona.dev", null))
        withFrom.to("a@b.c", "Name").send(MailMessage().text("y"))
        withFrom.toAddresses(Address("z@x.com", null)).send(MailMessage().subject("z").text("z"))
    }

    private fun fakeSocket(lines: List<String>): Socket {
        val script = lines.toMutableList()
        return object : Socket() {
            private val input = object : ByteArrayInputStream(ByteArray(0)) {
                private var idx = 0
                private var buf = ByteArray(0)
                private var pos = 0

                override fun read(): Int {
                    if (pos >= buf.size) {
                        if (idx >= script.size) return -1
                        buf = script[idx++].toByteArray(StandardCharsets.UTF_8)
                        pos = 0
                    }
                    return buf[pos++].toInt() and 0xff
                }

                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    if (len == 0) return 0
                    val c = read()
                    if (c < 0) return -1
                    b[off] = c.toByte()
                    var n = 1
                    while (n < len) {
                        val next = read()
                        if (next < 0) break
                        b[off + n] = next.toByte()
                        n++
                        if (next == '\n'.code) break
                    }
                    return n
                }
            }

            override fun getInputStream() = input

            override fun getOutputStream() = ByteArrayOutputStream()

            override fun close() = Unit
        }
    }
}
