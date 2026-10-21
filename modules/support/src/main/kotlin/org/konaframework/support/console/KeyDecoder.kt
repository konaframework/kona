package org.konaframework.support.console

import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * Turns raw stdin bytes into [TerminalKey]s: printable UTF-8, Backspace, Enter, arrows
 * (CSI and SS3), Home/End/Delete, Ctrl+C, and EOF.
 */
class KeyDecoder(private val source: ByteSource) {
    fun next(): TerminalKey {
        val lead = readByte() ?: return TerminalKey.Eof
        return when (lead) {
            0x03 -> TerminalKey.CtrlC
            0x04 -> TerminalKey.Eof
            0x09 -> TerminalKey.Tab
            0x0A, 0x0D -> {
                if (lead == 0x0D && peekAvailable() > 0) {
                    val nxt = readByte()
                    if (nxt != null && nxt != 0x0A) {
                        unread = nxt
                    }
                }
                TerminalKey.Enter
            }
            0x08, 0x7F -> TerminalKey.Backspace
            0x1B -> escape()
            else -> utf8(lead)
        }
    }

    private var unread: Int? = null

    private fun readByte(): Int? {
        val pending = unread
        if (pending != null) {
            unread = null
            return pending
        }
        val value =
            try {
                source.read()
            } catch (_: IOException) {
                return null
            }
        return if (value < 0) null else value and 0xFF
    }

    private fun peekAvailable(): Int =
        try {
            source.available()
        } catch (_: IOException) {
            0
        }

    private fun escape(): TerminalKey {
        // Do not trust InputStream.available(): WSL and many FileInputStream
        // stdin implementations report 0 even when CSI `\x1b[A` is already in
        // the kernel buffer. A blocking read of the next byte is required so
        // Up/Down reach confirm/choice widgets. EOF after ESC is still Esc.
        val second = readByte() ?: return TerminalKey.Esc
        return when (second) {
            '['.code -> csi()
            'O'.code -> ss3()
            else -> TerminalKey.Ignored
        }
    }

    private fun csi(): TerminalKey {
        val params = StringBuilder()
        while (true) {
            val b = readByte() ?: return TerminalKey.Esc
            if (b in 0x40..0x7E) {
                return csiFinal(b.toChar(), params.toString())
            }
            if (b in 0x20..0x3F) {
                params.append(b.toChar())
            } else {
                return TerminalKey.Ignored
            }
        }
    }

    private fun csiFinal(final: Char, params: String): TerminalKey =
        when (final) {
            'A' -> TerminalKey.Up
            'B' -> TerminalKey.Down
            'C' -> TerminalKey.Right
            'D' -> TerminalKey.Left
            'H' -> TerminalKey.Home
            'F' -> TerminalKey.End
            '~' -> csiTilde(params.substringBefore(';'))
            else -> TerminalKey.Ignored
        }

    private fun csiTilde(code: String): TerminalKey {
        if (code == "3") {
            return TerminalKey.Delete
        }
        if (code == "1" || code == "7") {
            return TerminalKey.Home
        }
        if (code == "4" || code == "8") {
            return TerminalKey.End
        }
        return TerminalKey.Ignored
    }

    private fun ss3(): TerminalKey {
        val b = readByte() ?: return TerminalKey.Esc
        if (b == 'A'.code) {
            return TerminalKey.Up
        }
        if (b == 'B'.code) {
            return TerminalKey.Down
        }
        if (b == 'C'.code) {
            return TerminalKey.Right
        }
        if (b == 'D'.code) {
            return TerminalKey.Left
        }
        if (b == 'H'.code) {
            return TerminalKey.Home
        }
        if (b == 'F'.code) {
            return TerminalKey.End
        }
        return TerminalKey.Ignored
    }

    private fun utf8(lead: Int): TerminalKey {
        val width =
            when {
                lead and 0x80 == 0 -> 1
                lead and 0xE0 == 0xC0 -> 2
                lead and 0xF0 == 0xE0 -> 3
                lead and 0xF8 == 0xF0 -> 4
                else -> return TerminalKey.Ignored
            }
        if (width == 1) {
            if (lead < 0x20) {
                return TerminalKey.Ignored
            }
            return TerminalKey.CharKey(lead.toChar().toString())
        }
        val bytes = ByteArray(width)
        bytes[0] = lead.toByte()
        for (i in 1 until width) {
            val next = readByte() ?: return TerminalKey.Ignored
            if (next and 0xC0 != 0x80) {
                unread = next
                return TerminalKey.Ignored
            }
            bytes[i] = next.toByte()
        }
        return decodeUtf8(bytes)
    }

    private fun decodeUtf8(bytes: ByteArray): TerminalKey {
        val decoder =
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            TerminalKey.CharKey(decoder.decode(ByteBuffer.wrap(bytes)).toString())
        } catch (_: CharacterCodingException) {
            TerminalKey.Ignored
        }
    }

    companion object {
        fun from(input: InputStream): KeyDecoder = KeyDecoder(InputStreamByteSource(input))
    }
}

class InputStreamByteSource(private val input: InputStream) : ByteSource {
    override fun read(): Int = input.read()

    override fun available(): Int =
        try {
            input.available()
        } catch (_: IOException) {
            0
        }
}
