package org.konaframework.broadcasting

/** Static entry point for broadcasting. */
object Broadcast {
    @Volatile
    private var manager: BroadcastManager? = null

    @Volatile
    private var channels: ChannelManager = ChannelManager()

    private val socketId = ThreadLocal<String?>()

    @JvmStatic
    fun setManager(manager: BroadcastManager) {
        this.manager = manager
    }

    @JvmStatic
    fun manager(): BroadcastManager =
        manager ?: throw IllegalStateException("Broadcast manager not configured")

    @JvmStatic
    fun channels(): ChannelManager = channels

    @JvmStatic
    fun setChannels(manager: ChannelManager) {
        channels = manager
    }

    @JvmStatic
    fun channel(name: String, authorizer: ChannelManager.Authorizer): ChannelManager =
        channels.channel(name, authorizer)

    @JvmStatic
    fun event(event: ShouldBroadcast) {
        manager().broadcast(event)
    }

    @JvmStatic
    fun to(vararg channels: Channel): PendingBroadcast = PendingBroadcast(channels.toList())

    /** Exclude the given socket from the next broadcast on this thread (`toOthers`). */
    @JvmStatic
    fun socket(socketId: String?): Broadcast {
        this.socketId.set(socketId)
        return this
    }

    @JvmStatic
    fun toOthers(): Broadcast {
        // Marker — [PendingBroadcast.toOthers] / [currentSocketId] read the thread-local.
        return this
    }

    @JvmStatic
    fun currentSocketId(): String? = socketId.get()

    @JvmStatic
    fun clearSocketId() {
        socketId.remove()
    }
}

class PendingBroadcast(
    private val channels: List<Channel>,
    private var excludeOthers: Boolean = false,
) {
    fun toOthers(): PendingBroadcast {
        excludeOthers = true
        return this
    }

    @JvmOverloads
    fun event(name: String, payload: Map<String, Any?> = emptyMap()) {
        val exclude = if (excludeOthers) Broadcast.currentSocketId() else null
        Broadcast.manager().connection().broadcast(channels, name, payload, exclude)
    }
}
