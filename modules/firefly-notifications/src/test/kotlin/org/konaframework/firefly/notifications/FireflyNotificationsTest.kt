package org.konaframework.firefly.notifications

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.konaframework.broadcasting.BroadcastManager
import org.konaframework.core.Config
import org.konaframework.firefly.actions.Action
import org.konaframework.firefly.support.FireflyException
import org.konaframework.notifications.DatabaseChannel
import org.konaframework.notifications.Notifiable
import org.konaframework.notifications.Notification
import org.konaframework.notifications.NotificationSender
import org.konaframework.http.RequestSession
import org.konaframework.session.ArraySessionStore
import org.konaframework.session.StoreSession
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue

class FireflyNotificationsTest {
    @AfterEach
    fun clear() {
        FireflyNotification.configureUsing(null)
    }

    @Test
    fun flashSendRenderAndPull() {
        val session = session()
        assertEquals("", FireflyNotification.render(session))
        val first = FireflyNotification.make("one")
            .title("Saved & \"ok\"")
            .body("Line <1>")
            .icon("check")
            .success()
            .duration(4)
            .extraAttributes(mapOf("data-kind" to "toast"))
            .actions(Action.make("open").label("Open"))
        first.send(session)
        val second = FireflyNotification.make("two").title("Next").info().send(session)
        val html = FireflyNotification.render(session)
        assertTrue(html.contains("ff-notifications"))
        assertTrue(html.contains("data-ff-notifications"))
        assertTrue(html.contains("Saved &amp; &quot;ok&quot;"))
        assertTrue(html.contains("Line &lt;1&gt;"))
        assertTrue(html.contains("ff-notification-icon"))
        assertTrue(html.contains("ff-notification-dismiss"))
        assertTrue(html.contains("ff-notification-progress"))
        assertTrue(html.contains("data-ff-status=\"success\""))
        assertTrue(html.contains("data-ff-duration=\"4\""))
        assertTrue(html.contains("data-kind=\"toast\""))
        assertTrue(html.contains("Open"))
        assertTrue(html.contains("Next"))
        assertTrue(html.contains("data-ff-status=\"info\""))
        val pulled = FireflyNotification.pull(session)
        assertTrue(pulled.contains("Saved"))
        assertEquals(null, session.get(FireflyNotification.FLASH_KEY))
        assertEquals("one", first.toMap()["id"])
        assertEquals("two", second.toMap()["id"])
    }

    @Test
    fun savedCreatedDeletedHelpers() {
        val session = session()
        FireflyToasts.saved(session)
        FireflyToasts.created(session)
        FireflyToasts.deleted(session)
        val html = FireflyNotification.pull(session)
        assertTrue(html.contains("Saved"))
        assertTrue(html.contains("Created"))
        assertTrue(html.contains("Deleted"))
        assertTrue(html.contains("data-ff-status=\"success\""))
    }

    @Test
    fun pullClearsPlainRequestSession() {
        val bag = LinkedHashMap<String, Any?>()
        val session = object : RequestSession {
            override fun get(key: String): Any? = bag[key]
            override fun put(key: String, value: Any?): RequestSession {
                bag[key] = value
                return this
            }
            override fun flash(key: String, value: Any?): RequestSession = put(key, value)
            override fun flashInput(input: Map<String, String>): RequestSession = this
            override fun old(key: String, default: String?): String? = default
            override fun oldInput(): Map<String, String> = emptyMap()
            override fun previousUrl(): String? = null
            override fun setPreviousUrl(url: String): RequestSession = this
        }
        assertEquals("", FireflyNotification.pull(session))
        FireflyNotification.make("n").title("Hi").success().send(session)
        assertTrue(FireflyNotification.pull(session).contains("Hi"))
        assertNull(session.get(FireflyNotification.FLASH_KEY))
    }

    @Test
    fun closuresStatusColorsAndDefaults() {
        val plain = FireflyNotification.make("plain").title("Hello")
        val plainHtml = plain.render()
        assertTrue(plainHtml.contains("data-ff-color=\"info\""))
        assertTrue(plainHtml.contains("data-ff-duration=\"6\""))
        assertFalse(plainHtml.contains("ff-notification-body"))
        assertTrue(plainHtml.contains("ff-notification-icon"))
        assertTrue(plainHtml.contains("ff-notification-accent"))
        assertFalse(plain.toMap().containsKey("body"))
        assertFalse(plain.toMap().containsKey("icon"))
        assertFalse(plain.toMap().containsKey("attributes"))
        assertFalse(plain.toMap().containsKey("actions"))

        val colored = FireflyNotification.make("colored")
            .color("teal")
            .success()
            .title { get -> get.get("/name").toString() }
            .body { " " }
            .icon { "bell" }
            .duration { 3 }
            .extraAttributes { mapOf("data-role" to "admin") }
            .warning()
        val html = colored.render(mapOf("name" to "Ada"))
        assertTrue(html.contains("Ada"))
        assertTrue(html.contains("data-ff-color=\"teal\""))
        assertTrue(html.contains("data-ff-status=\"warning\""))
        assertTrue(html.contains("data-ff-duration=\"3\""))
        assertTrue(html.contains("data-role=\"admin\""))
        assertTrue(html.contains("bell"))
        assertTrue(colored.toMap(mapOf("name" to "Ada")).containsKey("body"))

        val danger = FireflyNotification.make("danger").title("Stop").body("").icon("x").danger()
        assertTrue(danger.render().contains("data-ff-status=\"danger\""))
        assertTrue(danger.render().contains("data-ff-color=\"danger\""))
        assertFalse(danger.toMap().containsKey("body"))
        assertTrue(danger.toMap().containsKey("icon"))
        assertTrue(danger.render().contains("ff-icon"))

        val dangerDefault = FireflyNotification.make("danger-default").title("Boom").danger().render()
        assertTrue(dangerDefault.contains("ff-notification-icon"))
        assertTrue(dangerDefault.contains("data-ff-status=\"danger\""))

        val warningDefault = FireflyNotification.make("warn-default").title("Careful").warning().render()
        assertTrue(warningDefault.contains("ff-notification-icon"))
        assertTrue(warningDefault.contains("data-ff-status=\"warning\""))

        val successDefault = FireflyNotification.make("ok-default").title("Nice").success().render()
        assertTrue(successDefault.contains("ff-icon"))

        val named = FireflyNotification.make("named").title("Ok").icon("check").success().render()
        assertTrue(named.contains("ff-icon"))

        val zero = FireflyNotification.make("zero").title("Stay").duration(0).success().render()
        assertFalse(zero.contains("ff-notification-progress"))
    }

    @Test
    fun rejectsBlankValuesAndBadFlash() {
        assertThrows<FireflyException> { FireflyNotification.make(" ") }
        assertThrows<FireflyException> { FireflyNotification.make("n").title(" ") }
        assertThrows<FireflyException> { FireflyNotification.make("n").title("Ok").icon(" ") }
        assertThrows<FireflyException> { FireflyNotification.make("n").title("Ok").duration(-1) }
        assertThrows<FireflyException> {
            FireflyNotification.make("n").title { " " }.render()
        }
        assertThrows<FireflyException> {
            FireflyNotification.make("n").title("Ok").icon { " " }.render()
        }
        assertThrows<FireflyException> {
            FireflyNotification.make("n").title("Ok").duration { -2 }.render()
        }
        assertThrows<FireflyException> {
            FireflyNotification.make("n").broadcastChannel(" ").title("Ok")
        }
        val session = session()
        session.put(FireflyNotification.FLASH_KEY, "nope")
        assertThrows<FireflyException> {
            FireflyNotification.make("n").title("Ok").send(session)
        }
        assertThrows<FireflyException> { FireflyNotification.render(session) }
        session.forget(FireflyNotification.FLASH_KEY)
        session.put(FireflyNotification.FLASH_KEY, listOf("skip", mapOf("title" to "Kept")))
        FireflyNotification.make("n").title("Added").send(session)
        val html = FireflyNotification.render(session)
        assertTrue(html.contains("Kept"))
        assertTrue(html.contains("Added"))
        session.forget(FireflyNotification.FLASH_KEY)
        session.put(FireflyNotification.FLASH_KEY, listOf("skip-only", 1, true))
        assertEquals("", FireflyNotification.render(session))
    }

    @Test
    fun rendersStoredMaps() {
        val session = session()
        session.flash(
            FireflyNotification.FLASH_KEY,
            listOf(
                mapOf("duration" to 2, "title" to "Num", "status" to "success", "color" to "success", "body" to "b", "icon" to "i"),
                mapOf("duration" to "4", "attributes" to mapOf("data-a" to null, "data-b" to "c")),
                mapOf("duration" to "nope"),
                mapOf("duration" to true, "attributes" to "nope"),
                "skip",
            ),
        )
        val html = FireflyNotification.render(session)
        assertTrue(html.contains("data-ff-duration=\"2\""))
        assertTrue(html.contains("data-ff-duration=\"4\""))
        assertTrue(html.contains("data-ff-duration=\"6\""))
        assertTrue(html.contains("data-a=\"\""))
        assertTrue(html.contains("data-b=\"c\""))
        assertTrue(html.contains("Num"))
    }

    @Test
    fun databaseBellAndSender() {
        val channel = DatabaseChannel()
        val user = Person("ada")
        val notice = FireflyNotification.make("db").title("Stored").body("Body").success()
        notice.sendToDatabase(user, channel)
        notice.sendToDatabase(user, channel, mapOf("name" to "Ada"))
        val sender = NotificationSender().extend("database", channel)
        notice.sendToDatabase(user, sender)
        notice.sendToDatabase(user, sender, emptyMap())
        val bell = NotificationBell(channel, "ada")
        assertEquals(4, bell.records().size)
        assertEquals(4, bell.unread().size)
        val html = bell.render()
        assertTrue(html.contains("Stored"))
        assertTrue(html.contains("data-ff-unread=\"true\""))
        assertTrue(html.contains("ff-notification-count\">4"))
        val id = bell.records().first().id
        assertTrue(bell.markAsRead(id))
        assertFalse(bell.markAsRead("missing"))
        assertTrue(bell.render().contains("data-ff-unread=\"false\""))
        assertEquals(3, bell.markAllAsRead())
        assertEquals(0, bell.markAllAsRead())
        assertEquals(0, bell.unread().size)

        channel.send(user, TypelessNotice())
        assertTrue(NotificationBell(channel, "ada").render().contains("TypelessNotice"))
    }

    @Test
    fun broadcastAndRefresh() {
        val manager = manager()
        val quiet = Person("ada", null)
        val routed = Person("ada", "room")
        assertThrows<FireflyException> {
            FireflyNotification.make("b").title("Hi").broadcast(quiet, manager)
        }
        assertThrows<FireflyException> {
            FireflyNotification.make("b").title("Hi").broadcastChannel { " " }.broadcast(quiet, manager)
        }
        FireflyNotification.make("b").title("Hi").broadcastChannel("admin").broadcast(quiet, manager)
        FireflyNotification.make("b").title { "Live" }.broadcastChannel { "admin" }.broadcast(routed, manager, mapOf("x" to 1))
        val messages = manager.log().messages()
        assertEquals(2, messages.size)
        assertEquals("firefly.notification", messages[0].event)
        assertEquals(listOf("admin"), messages[0].channels)
        assertEquals("Hi", messages[0].payload["title"])
        assertEquals(null, messages[0].payload["route"])
        assertEquals("room", messages[1].payload["route"])
        assertEquals("Live", messages[1].payload["title"])

        val refresh = BroadcastRefresh().channel("admin")
        assertTrue(refresh.render().contains("data-ff-event=\"firefly.notification\""))
        assertTrue(refresh.render().contains("data-ff-channel=\"admin\""))
        val closed = BroadcastRefresh().channel { get -> get.get("/room").toString() }.event { "panel.ping" }
        assertTrue(closed.render(mapOf("room" to "west")).contains("data-ff-channel=\"west\""))
        assertTrue(closed.render(mapOf("room" to "west")).contains("data-ff-event=\"panel.ping\""))
        assertThrows<FireflyException> { BroadcastRefresh().render() }
        assertThrows<FireflyException> { BroadcastRefresh().channel(" ").render() }
        assertThrows<FireflyException> { BroadcastRefresh().channel("admin").event(" ").render() }
        assertThrows<FireflyException> { BroadcastRefresh().channel { "" }.render() }
        assertThrows<FireflyException> { BroadcastRefresh().channel("admin").event { " " }.render() }
    }

    @Test
    fun configureUsingAppliesToLaterInstances() {
        FireflyNotification.configureUsing { it.title("Default").duration(1) }
        val made = FireflyNotification.make("cfg")
        assertEquals("Default", made.toMap()["title"])
        assertEquals(1, made.toMap()["duration"])
        FireflyNotification.configureUsing(null)
        val generated = FireflyNotification.make().title("Generated")
        assertTrue(generated.render().contains("Generated"))
        assertThrows<FireflyException> { FireflyNotification.make("later").render() }
    }

    private fun session(): StoreSession = StoreSession("s", ArraySessionStore())

    private fun manager(): BroadcastManager {
        val config = Config()
        config.set("broadcasting.default", "log")
        return BroadcastManager(config)
    }

    private class TypelessNotice : Notification() {
        override fun via(notifiable: Notifiable): List<String> = listOf("database")

        override fun toArray(notifiable: Notifiable): Map<String, Any?> = emptyMap()
    }

    private class Person(private val id: String, private val broadcast: String? = id) : Notifiable {
        override fun routeNotificationFor(channel: String): String? {
            if (channel == "broadcast") return broadcast
            return id
        }
    }
}
