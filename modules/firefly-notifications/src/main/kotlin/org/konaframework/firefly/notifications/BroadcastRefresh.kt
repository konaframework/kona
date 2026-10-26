package org.konaframework.firefly.notifications

import org.konaframework.firefly.schemas.Get
import org.konaframework.firefly.support.FireflyException
import java.util.function.Function

/**
 * Marks a page that should refresh when a Firefly notification is broadcast.
 */
class BroadcastRefresh {
    private val channelSetting = TextSetting()
    private val eventSetting = TextSetting()

    fun channel(name: String): BroadcastRefresh = apply {
        if (name.isBlank()) throw FireflyException("Broadcast refresh channel is blank")
        channelSetting.setLiteral(name)
    }

    fun channel(provider: Function<Get, String>): BroadcastRefresh = apply { channelSetting.setUsing(provider) }

    fun event(name: String): BroadcastRefresh = apply {
        if (name.isBlank()) throw FireflyException("Broadcast refresh event is blank")
        eventSetting.setLiteral(name)
    }

    fun event(provider: Function<Get, String>): BroadcastRefresh = apply { eventSetting.setUsing(provider) }

    fun render(): String = render(emptyMap())

    fun render(state: Map<String, Any?>): String {
        val get = SchemaBridge.open(state).second
        val channelName = channelSetting.resolve(get)
        if (channelName == null || channelName.isBlank()) {
            throw FireflyException("Broadcast refresh channel is blank")
        }
        val resolvedEvent = eventSetting.resolve(get)
        val eventName = if (resolvedEvent == null) "firefly.notification" else resolvedEvent
        if (eventName.isBlank()) {
            throw FireflyException("Broadcast refresh event is blank")
        }
        return """<div class="ff-broadcast-refresh" data-ff-channel="${escapeHtml(channelName)}" data-ff-event="${escapeHtml(eventName)}" wire:poll.15s></div>"""
    }
}
