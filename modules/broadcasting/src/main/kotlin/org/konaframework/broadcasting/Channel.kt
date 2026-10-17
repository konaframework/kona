package org.konaframework.broadcasting

/** Named broadcast channel (public / private). */
class Channel(
    val name: String,
    val restricted: Boolean,
) {
    fun isPrivate(): Boolean = restricted

    companion object {
        @JvmStatic
        fun publicChannel(name: String): Channel = Channel(name, false)

        @JvmStatic
        fun privateOf(name: String): Channel = Channel(name, true)
    }
}
