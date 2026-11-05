package org.konaframework.broadcasting

/** Named broadcast channel (public / private / presence). */
open class Channel(
    val name: String,
    val restricted: Boolean,
) {
    fun isPrivate(): Boolean = restricted || name.startsWith("private-") || name.startsWith("presence-")

    open fun isPresence(): Boolean = name.startsWith("presence-")

    /** Pusher wire name (`private-` / `presence-` prefixes applied when needed). */
    open fun pusherName(): String {
        if (name.startsWith("private-") || name.startsWith("presence-")) {
            return name
        }
        if (restricted) {
            return "private-$name"
        }
        return name
    }

    companion object {
        @JvmStatic
        fun publicChannel(name: String): Channel = Channel(name, false)

        @JvmStatic
        fun privateOf(name: String): Channel = Channel(name, true)

        @JvmStatic
        fun presence(name: String): PresenceChannel = PresenceChannel(name)
    }
}

/** Presence channel carrying member info for authorization responses. */
class PresenceChannel(
    name: String,
) : Channel(
    name = if (name.startsWith("presence-")) name.removePrefix("presence-") else name,
    restricted = true,
) {
    override fun isPresence(): Boolean = true

    override fun pusherName(): String = "presence-$name"
}
