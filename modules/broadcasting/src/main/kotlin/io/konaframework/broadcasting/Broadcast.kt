package io.konaframework.broadcasting

/** Static entry point for broadcasting. */
object Broadcast {
    @Volatile
    private var manager: BroadcastManager? = null

    @JvmStatic
    fun setManager(manager: BroadcastManager) {
        this.manager = manager
    }

    @JvmStatic
    fun manager(): BroadcastManager =
        manager ?: throw IllegalStateException("Broadcast manager not configured")

    @JvmStatic
    fun event(event: ShouldBroadcast) {
        manager().broadcast(event)
    }

    @JvmStatic
    fun to(vararg channels: Channel): PendingBroadcast = PendingBroadcast(channels.toList())
}

class PendingBroadcast(private val channels: List<Channel>) {
    fun event(name: String, payload: Map<String, Any?> = emptyMap()) {
        Broadcast.manager().connection().broadcast(channels, name, payload)
    }
}
