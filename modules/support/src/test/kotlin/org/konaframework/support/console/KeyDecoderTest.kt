package org.konaframework.support.console

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

class KeyDecoderTest {
    private fun decode(vararg bytes: Int, available: Int? = null): List<TerminalKey> {
        val data = bytes.map { it.and(0xFF).toByte() }.toByteArray()
        val decoder = KeyDecoder(BufferSource(data, available))
        val keys = mutableListOf<TerminalKey>()
        while (true) {
            val key = decoder.next()
            keys.add(key)
            if (key is TerminalKey.Eof) {
                break
            }
        }
        return keys
    }

    @Test
    fun asciiEnterAndBackspace() {
        val keys = decode(0x41, 0x0D, 0x0A, 0x08, 0x7F, 0x09, 0x03, 0x04)
        assertEquals(TerminalKey.CharKey("A"), keys[0])
        assertEquals(TerminalKey.Enter, keys[1])
        assertEquals(TerminalKey.Backspace, keys[2])
        assertEquals(TerminalKey.Backspace, keys[3])
        assertEquals(TerminalKey.Tab, keys[4])
        assertEquals(TerminalKey.CtrlC, keys[5])
        assertEquals(TerminalKey.Eof, keys[6])
    }

    @Test
    fun lfEnterAndCrWithUnread() {
        assertEquals(TerminalKey.Enter, decode(0x0A)[0])
        val crThenA = decode(0x0D, 0x41)
        assertEquals(TerminalKey.Enter, crThenA[0])
        assertEquals(TerminalKey.CharKey("A"), crThenA[1])
    }

    @Test
    fun arrowsCsiAndSs3() {
        assertEquals(TerminalKey.Up, decode(0x1B, '['.code, 'A'.code)[0])
        assertEquals(TerminalKey.Down, decode(0x1B, '['.code, 'B'.code)[0])
        assertEquals(TerminalKey.Right, decode(0x1B, '['.code, 'C'.code)[0])
        assertEquals(TerminalKey.Left, decode(0x1B, '['.code, 'D'.code)[0])
        assertEquals(TerminalKey.Home, decode(0x1B, '['.code, 'H'.code)[0])
        assertEquals(TerminalKey.End, decode(0x1B, '['.code, 'F'.code)[0])
        assertEquals(TerminalKey.Up, decode(0x1B, 'O'.code, 'A'.code)[0])
        assertEquals(TerminalKey.Down, decode(0x1B, 'O'.code, 'B'.code)[0])
        assertEquals(TerminalKey.Right, decode(0x1B, 'O'.code, 'C'.code)[0])
        assertEquals(TerminalKey.Left, decode(0x1B, 'O'.code, 'D'.code)[0])
        assertEquals(TerminalKey.Home, decode(0x1B, 'O'.code, 'H'.code)[0])
        assertEquals(TerminalKey.End, decode(0x1B, 'O'.code, 'F'.code)[0])
        assertEquals(TerminalKey.Ignored, decode(0x1B, 'O'.code, 'Z'.code)[0])
        assertEquals(TerminalKey.Esc, decode(0x1B, 'O'.code)[0])
    }

    @Test
    fun csiTildeAndParams() {
        assertEquals(TerminalKey.Delete, decode(0x1B, '['.code, '3'.code, '~'.code)[0])
        assertEquals(TerminalKey.Home, decode(0x1B, '['.code, '1'.code, '~'.code)[0])
        assertEquals(TerminalKey.Home, decode(0x1B, '['.code, '7'.code, '~'.code)[0])
        assertEquals(TerminalKey.End, decode(0x1B, '['.code, '4'.code, '~'.code)[0])
        assertEquals(TerminalKey.End, decode(0x1B, '['.code, '8'.code, '~'.code)[0])
        assertEquals(TerminalKey.Ignored, decode(0x1B, '['.code, '5'.code, '~'.code)[0])
        assertEquals(TerminalKey.Up, decode(0x1B, '['.code, '1'.code, ';'.code, '5'.code, 'A'.code)[0])
        assertEquals(TerminalKey.Ignored, decode(0x1B, '['.code, 'Z'.code)[0])
        assertEquals(TerminalKey.Ignored, decode(0x1B, '['.code, 0x01)[0])
        assertEquals(TerminalKey.Ignored, decode(0x1B, '['.code, 0x7F)[0])
        assertEquals(TerminalKey.Up, decode(0x1B, '['.code, '?'.code, 'A'.code)[0])
        assertEquals(TerminalKey.Home, decode(0x1B, '['.code, '1'.code, ';'.code, '3'.code, '~'.code)[0])
        assertEquals(TerminalKey.Esc, decode(0x1B, '['.code)[0])
        assertEquals(TerminalKey.Ignored, decode(0x1B, 'x'.code)[0])
        assertEquals(TerminalKey.Esc, decode(0x1B, available = 4)[0])
    }

    @Test
    fun loneEscWhenNothingAvailable() {
        assertEquals(TerminalKey.Esc, decode(0x1B, available = 0)[0])
        assertEquals(TerminalKey.Esc, decode(0x1B)[0])
    }

    @Test
    fun arrowsWhenAvailableReportsZero() {
        assertEquals(TerminalKey.Up, decode(0x1B, '['.code, 'A'.code, available = 0)[0])
        assertEquals(TerminalKey.Down, decode(0x1B, '['.code, 'B'.code, available = 0)[0])
        assertEquals(TerminalKey.Right, decode(0x1B, '['.code, 'C'.code, available = 0)[0])
        assertEquals(TerminalKey.Left, decode(0x1B, '['.code, 'D'.code, available = 0)[0])
        assertEquals(TerminalKey.Up, decode(0x1B, 'O'.code, 'A'.code, available = 0)[0])
        assertEquals(TerminalKey.Down, decode(0x1B, 'O'.code, 'B'.code, available = 0)[0])
    }

    @Test
    fun utf8AndInvalidLead() {
        val cafe = "é".toByteArray(Charsets.UTF_8).map { it.toInt() and 0xFF }
        assertEquals(TerminalKey.CharKey("é"), decode(*cafe.toIntArray())[0])
        val snow = "☃".toByteArray(Charsets.UTF_8).map { it.toInt() and 0xFF }
        assertEquals(TerminalKey.CharKey("☃"), decode(*snow.toIntArray())[0])
        val grin = "😀".toByteArray(Charsets.UTF_8).map { it.toInt() and 0xFF }
        assertEquals(TerminalKey.CharKey("😀"), decode(*grin.toIntArray())[0])
        assertEquals(TerminalKey.Ignored, decode(0x80)[0])
        assertEquals(TerminalKey.Ignored, decode(0x01)[0])
        assertEquals(TerminalKey.Ignored, decode(0xC2)[0])
        val truncatedThenA = decode(0xC2, 0x41)
        assertEquals(TerminalKey.Ignored, truncatedThenA[0])
        assertEquals(TerminalKey.CharKey("A"), truncatedThenA[1])
        assertEquals(TerminalKey.Ignored, decode(0xC0, 0x80)[0])
        assertEquals(TerminalKey.Enter, decode(0x0D, available = 8)[0])
    }

    @Test
    fun companionFromAndThrowingStream() {
        val decoder = KeyDecoder.from(ByteArrayInputStream("Z".toByteArray()))
        assertEquals(TerminalKey.CharKey("Z"), decoder.next())
        val ioRead =
            object : InputStream() {
                override fun read(): Int = throw IOException("closed")
            }
        assertEquals(TerminalKey.Eof, KeyDecoder.from(ioRead).next())
        val ioAvail =
            object : InputStream() {
                private var n = 0

                override fun read(): Int = if (n++ == 0) 0x1B else -1

                override fun available(): Int = throw IOException("avail")
            }
        assertEquals(TerminalKey.Esc, KeyDecoder.from(ioAvail).next())
        val crAvailThrow =
            object : InputStream() {
                private var n = 0

                override fun read(): Int = if (n++ == 0) 0x0D else 0x41

                override fun available(): Int = throw IOException("avail")
            }
        assertEquals(TerminalKey.Enter, KeyDecoder.from(crAvailThrow).next())
        val src = InputStreamByteSource(ByteArrayInputStream(byteArrayOf(0x20)))
        assertEquals(0x20, src.read())
        assertEquals(0, src.available())
        val throwAvail =
            object : ByteSource {
                private var n = 0

                override fun read(): Int = if (n++ == 0) 0x0D else -1

                override fun available(): Int = throw IOException("avail")
            }
        assertEquals(TerminalKey.Enter, KeyDecoder(throwAvail).next())
    }

}

internal class BufferSource(
    bytes: ByteArray,
    private val availableOverride: Int? = null,
) : ByteSource {
    private val data = bytes.map { it.toInt() and 0xFF }.toMutableList()

    override fun read(): Int = if (data.isEmpty()) -1 else data.removeAt(0)

    override fun available(): Int = availableOverride ?: data.size
}
