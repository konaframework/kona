package org.konaframework.firefly.notifications

import org.konaframework.broadcasting.BroadcastManager
import org.konaframework.broadcasting.Channel
import org.konaframework.broadcasting.ShouldBroadcast
import org.konaframework.firefly.actions.Action
import org.konaframework.firefly.schemas.Get
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.schemas.htmlAttributes
import org.konaframework.firefly.support.FireflyException
import org.konaframework.firefly.support.Icon
import org.konaframework.http.RequestSession
import org.konaframework.notifications.DatabaseChannel
import org.konaframework.notifications.Notifiable
import org.konaframework.notifications.Notification
import org.konaframework.notifications.NotificationSender
import org.konaframework.session.Session
import java.util.UUID
import java.util.function.Consumer
import java.util.function.Function

/**
 * A toast, database row, or broadcast that a panel can show.
 *
 * `title`, `body`, `icon`, `color`, `duration`, and `extraAttributes` take a static value or a
 * closure. The closure receives the schema [Get] reader. Absolute paths start with `/`.
 */
open class FireflyNotification private constructor(private val id: String) {
    private val titleSetting = TextSetting()
    private val bodySetting = TextSetting()
    private val iconSetting = TextSetting()
    private val colorSetting = TextSetting()
    private val channelSetting = TextSetting()
    private val durationSetting = IntSetting(6)
    private val attributes = AttributeSetting()
    private val actionList = ArrayList<Action>()
    private var statusName: String = "info"
    private var colorExplicit: Boolean = false

    fun title(value: String): FireflyNotification = apply { titleSetting.setLiteral(requireTitle(value)) }

    fun title(provider: Function<Get, String>): FireflyNotification = apply { titleSetting.setUsing(provider) }

    fun body(value: String): FireflyNotification = apply { bodySetting.setLiteral(value) }

    fun body(provider: Function<Get, String>): FireflyNotification = apply { bodySetting.setUsing(provider) }

    fun icon(value: String): FireflyNotification = apply {
        if (value.isBlank()) throw FireflyException("Notification icon is blank")
        iconSetting.setLiteral(value)
    }

    fun icon(provider: Function<Get, String>): FireflyNotification = apply { iconSetting.setUsing(provider) }

    fun color(value: String): FireflyNotification = apply {
        colorExplicit = true
        colorSetting.setLiteral(value)
    }

    fun color(provider: Function<Get, String>): FireflyNotification = apply {
        colorExplicit = true
        colorSetting.setUsing(provider)
    }

    fun duration(seconds: Int): FireflyNotification = apply { durationSetting.setLiteral(seconds) }

    fun duration(provider: Function<Get, Int>): FireflyNotification = apply { durationSetting.setUsing(provider) }

    fun extraAttributes(values: Map<String, String>): FireflyNotification = apply { attributes.setLiteral(values) }

    fun extraAttributes(provider: Function<Get, Map<String, String>>): FireflyNotification = apply {
        attributes.setUsing(provider)
    }

    fun actions(vararg actions: Action): FireflyNotification = apply {
        actionList.clear()
        actionList.addAll(actions)
    }

    fun broadcastChannel(name: String): FireflyNotification = apply {
        if (name.isBlank()) throw FireflyException("Notification broadcast channel is blank")
        channelSetting.setLiteral(name)
    }

    fun broadcastChannel(provider: Function<Get, String>): FireflyNotification = apply {
        channelSetting.setUsing(provider)
    }

    fun success(): FireflyNotification = status("success")

    fun info(): FireflyNotification = status("info")

    fun warning(): FireflyNotification = status("warning")

    fun danger(): FireflyNotification = status("danger")

    fun send(session: RequestSession): FireflyNotification = send(session, emptyMap())

    fun send(session: RequestSession, state: Map<String, Any?>): FireflyNotification {
        val payload = toMap(state)
        val existing = session.get(FLASH_KEY)
        val list = ArrayList<Map<String, Any?>>()
        if (existing is List<*>) {
            for (item in existing) {
                if (item is Map<*, *>) list.add(copyMap(item))
            }
        } else if (existing != null) {
            throw FireflyException("Session flash [$FLASH_KEY] is not a list")
        }
        list.add(payload)
        session.flash(FLASH_KEY, list)
        return this
    }

    fun sendToDatabase(notifiable: Notifiable, channel: DatabaseChannel): FireflyNotification =
        sendToDatabase(notifiable, channel, emptyMap())

    fun sendToDatabase(
        notifiable: Notifiable,
        channel: DatabaseChannel,
        state: Map<String, Any?>,
    ): FireflyNotification {
        channel.send(notifiable, StoredNotice(toMap(state)))
        return this
    }

    fun sendToDatabase(notifiable: Notifiable, sender: NotificationSender): FireflyNotification =
        sendToDatabase(notifiable, sender, emptyMap())

    fun sendToDatabase(
        notifiable: Notifiable,
        sender: NotificationSender,
        state: Map<String, Any?>,
    ): FireflyNotification {
        sender.sendNow(notifiable, StoredNotice(toMap(state)))
        return this
    }

    fun broadcast(notifiable: Notifiable, manager: BroadcastManager): FireflyNotification =
        broadcast(notifiable, manager, emptyMap())

    fun broadcast(
        notifiable: Notifiable,
        manager: BroadcastManager,
        state: Map<String, Any?>,
    ): FireflyNotification {
        val get = SchemaBridge.open(state).second
        val channelName = channelSetting.resolve(get)
        if (channelName == null || channelName.isBlank()) {
            throw FireflyException("Notification [$id] has no broadcast channel")
        }
        val payload = LinkedHashMap(toMap(state))
        val route = notifiable.routeNotificationFor("broadcast")
        if (route != null) payload["route"] = route
        manager.broadcast(NoticeBroadcast(channelName, payload))
        return this
    }

    fun render(): String = render(emptyMap())

    fun render(state: Map<String, Any?>): String {
        val resolved = resolved(state)
        return toast(
            resolved.title,
            resolved.body,
            resolved.icon,
            statusName,
            resolved.color,
            resolved.duration,
            resolved.attributes,
            actionHtml(),
        )
    }

    fun toMap(): Map<String, Any?> = toMap(emptyMap())

    fun toMap(state: Map<String, Any?>): Map<String, Any?> {
        val resolved = resolved(state)
        val payload = linkedMapOf<String, Any?>(
            "id" to id,
            "title" to resolved.title,
            "status" to statusName,
            "color" to resolved.color,
            "duration" to resolved.duration,
        )
        if (resolved.body.isNotEmpty()) payload["body"] = resolved.body
        if (resolved.icon.isNotEmpty()) payload["icon"] = resolved.icon
        if (resolved.attributes.isNotEmpty()) payload["attributes"] = resolved.attributes
        if (actionList.isNotEmpty()) {
            payload["actions"] = actionList.map { it.name }
            payload["actionsHtml"] = actionHtml()
        }
        return payload
    }

    private fun resolved(state: Map<String, Any?>): Resolved {
        val get = SchemaBridge.open(state).second
        val title = titleSetting.resolve(get)
        if (title == null || title.isBlank()) throw FireflyException("Notification [$id] title is blank")
        val icon = iconSetting.resolve(get)
        if (icon != null && icon.isBlank()) throw FireflyException("Notification icon is blank")
        val body = bodySetting.resolve(get) ?: ""
        val color = colorSetting.resolve(get) ?: statusName
        return Resolved(title, body, icon ?: "", color, durationSetting.resolve(get), attributes.resolve(get))
    }

    private fun actionHtml(): String {
        if (actionList.isEmpty()) return ""
        return Schema().components(*actionList.toTypedArray()).render()
    }

    private fun status(name: String): FireflyNotification = apply {
        statusName = name
        if (!colorExplicit) colorSetting.setLiteral(name)
    }

    private class Resolved(
        val title: String,
        val body: String,
        val icon: String,
        val color: String,
        val duration: Int,
        val attributes: Map<String, String>,
    )

    private class StoredNotice(private val payload: Map<String, Any?>) : Notification() {
        override fun via(notifiable: Notifiable): List<String> = listOf("database")

        override fun toArray(notifiable: Notifiable): Map<String, Any?> = payload
    }

    private class NoticeBroadcast(
        private val channelName: String,
        private val payload: Map<String, Any?>,
    ) : ShouldBroadcast {
        override fun broadcastOn(): List<Channel> = listOf(Channel.publicChannel(channelName))

        override fun broadcastAs(): String = "firefly.notification"

        override fun broadcastWith(): Map<String, Any?> = payload
    }

    companion object {
        const val FLASH_KEY: String = "firefly.notifications"

        private var configurer: Consumer<FireflyNotification>? = null

        @JvmStatic
        fun configureUsing(block: Consumer<FireflyNotification>?) {
            configurer = block
        }

        @JvmStatic
        fun make(): FireflyNotification = make(UUID.randomUUID().toString())

        @JvmStatic
        fun make(id: String): FireflyNotification {
            if (id.isBlank()) throw FireflyException("Notification id is blank")
            val notice = FireflyNotification(id)
            val block = configurer
            if (block != null) block.accept(notice)
            return notice
        }

        @JvmStatic
        fun render(session: RequestSession): String {
            val existing = session.get(FLASH_KEY)
            if (existing == null) return ""
            if (existing !is List<*>) throw FireflyException("Session flash [$FLASH_KEY] is not a list")
            val out = StringBuilder()
            for (item in existing) {
                if (item is Map<*, *>) out.append(toastFrom(item))
            }
            if (out.isEmpty()) return ""
            // Toast stack uses ff-toast-stack — not ff-notifications (bell menu in the topbar) (#397).
            return """<div class="ff-toast-stack" data-ff-notifications aria-live="polite">$out</div>"""
        }

        @JvmStatic
        fun pull(session: RequestSession): String {
            val html = render(session)
            if (html.isEmpty()) return html
            if (session is Session) {
                session.forget(FLASH_KEY)
            } else {
                session.put(FLASH_KEY, null)
            }
            return html
        }

        private fun text(item: Map<*, *>, key: String, fallback: String): String {
            val value = item[key]
            if (value == null) return fallback
            return value.toString()
        }

        private fun toastFrom(item: Map<*, *>): String {
            val title = text(item, "title", "")
            val body = text(item, "body", "")
            val icon = text(item, "icon", "")
            val color = text(item, "color", "info")
            val status = text(item, "status", color)
            val duration = when (val raw = item["duration"]) {
                is Number -> raw.toInt()
                is String -> raw.toIntOrNull() ?: 6
                else -> 6
            }
            val attributes = linkedMapOf<String, String>()
            val rawAttributes = item["attributes"]
            if (rawAttributes is Map<*, *>) {
                for ((key, value) in rawAttributes) {
                    attributes[key.toString()] = if (value == null) "" else value.toString()
                }
            }
            val storedActions = item["actionsHtml"]
            val actionHtml = if (storedActions == null) "" else storedActions.toString()
            return toast(title, body, icon, status, color, duration, attributes, actionHtml)
        }

        private fun toast(
            title: String,
            body: String,
            icon: String,
            status: String,
            color: String,
            duration: Int,
            attributes: Map<String, String>,
            actions: String,
        ): String {
            val iconHtml = """<span class="ff-notification-icon" aria-hidden="true">${iconMarkup(icon, status)}</span>"""
            val bodyHtml = if (body.isEmpty()) "" else """<p class="ff-notification-body">${escapeHtml(body)}</p>"""
            val actionHtml = if (actions.isEmpty()) "" else """<div class="ff-notification-actions">$actions</div>"""
            val progress =
                if (duration <= 0) {
                    ""
                } else {
                    """<div class="ff-notification-progress" aria-hidden="true"><span style="animation-duration:${duration}s"></span></div>"""
                }
            return """<div class="ff-notification" data-ff-status="${escapeHtml(status)}" data-ff-color="${escapeHtml(color)}" data-ff-duration="$duration" role="status"${htmlAttributes(attributes)}><span class="ff-notification-accent" aria-hidden="true"></span>$iconHtml<div class="ff-notification-main"><p class="ff-notification-title">${escapeHtml(title)}</p>$bodyHtml$actionHtml</div><button type="button" class="ff-notification-dismiss" data-ff-notification-dismiss aria-label="Dismiss">${Icon.X.svg()}</button>$progress</div>"""
        }

        private fun iconMarkup(icon: String, status: String): String {
            if (icon.isNotEmpty()) {
                val named = namedIcon(icon)
                if (named != null) return named.svg()
                return escapeHtml(icon)
            }
            return when (status) {
                "success" -> Icon.CHECK.svg()
                "danger" -> Icon.X.svg()
                "warning" -> WARNING_SVG
                else -> INFO_SVG
            }
        }

        private fun namedIcon(name: String): Icon? =
            try {
                Icon.fromName(name)
            } catch (_: FireflyException) {
                null
            }

        private fun copyMap(item: Map<*, *>): Map<String, Any?> {
            val copy = linkedMapOf<String, Any?>()
            for ((key, value) in item) copy[key.toString()] = value
            return copy
        }

        private fun requireTitle(value: String): String {
            if (value.isBlank()) throw FireflyException("Notification title is blank")
            return value
        }

        private val INFO_SVG: String =
            org.konaframework.icons.Icons.svg("heroicon-o-information-circle").className("ff-icon").toHtml()

        private val WARNING_SVG: String =
            org.konaframework.icons.Icons.svg("heroicon-o-exclamation-triangle").className("ff-icon").toHtml()
    }
}
