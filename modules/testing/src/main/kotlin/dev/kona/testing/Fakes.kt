package dev.kona.testing

import dev.kona.broadcasting.BroadcastManager
import dev.kona.broadcasting.ShouldBroadcast
import dev.kona.cache.ArrayCacheStore
import dev.kona.cache.Repository
import dev.kona.core.Config
import dev.kona.events.EventDispatcher
import dev.kona.filesystem.FilesystemAdapter
import dev.kona.mail.LogTransport
import dev.kona.mail.MailMessage
import dev.kona.mail.Mailer
import dev.kona.notifications.DatabaseChannel
import dev.kona.notifications.LogChannel
import dev.kona.notifications.Notifiable
import dev.kona.notifications.Notification
import dev.kona.notifications.NotificationSender
import dev.kona.queue.Bus
import dev.kona.queue.Job
import dev.kona.queue.QueueManager
import java.util.concurrent.CopyOnWriteArrayList

/** Fake mailer backed by [LogTransport]. */
class FakeMail {
    val transport = LogTransport()
    val mailer = Mailer(transport, null)
    fun messages(): List<MailMessage> = transport.messages()
}

/** Fake queue using the array driver. */
class FakeQueue {
    val manager = QueueManager(
        Config().also {
            it.set("queue.default", "array")
            it.set("queue.connections.array.driver", "array")
        },
        null,
        null,
    )
    val bus = Bus(manager)
    fun dispatch(job: Job) = bus.dispatch(job)
    fun size(): Long = manager.connection().size()
}

/** Fake notifications using in-memory database + log channels. */
class FakeNotification {
    val database = DatabaseChannel()
    val log = LogChannel()
    val sender = NotificationSender()
        .extend("database", database)
        .extend("log", log)

    fun send(notifiable: Notifiable, notification: Notification) {
        sender.send(notifiable, notification)
    }

    fun assertSent(type: Class<out Notification>): Boolean =
        database.all().any { it["type"] == type.name } ||
            log.entries().any { it.contains("notify ${type.simpleName} ") }
}

/** Fake event dispatcher that records every [dispatch] call. */
class FakeEvent {
    val dispatcher = EventDispatcher()
    private val recorded = CopyOnWriteArrayList<Any>()

    fun dispatch(event: Any) {
        recorded += event
        dispatcher.dispatch(event)
    }

    fun events(): List<Any> = recorded.toList()

    fun assertDispatched(type: Class<*>): Boolean = recorded.any { type.isInstance(it) }

    fun clear() {
        recorded.clear()
        dispatcher.flush()
    }
}

/** In-memory cache repository backed by [ArrayCacheStore]. */
class FakeCache {
    val store = ArrayCacheStore()
    val repository = Repository(store)

    fun get(key: String): Any? = repository.get(key)

    @JvmOverloads
    fun put(key: String, value: Any?, seconds: Long = 60): Boolean = repository.put(key, value, seconds)

    fun forever(key: String, value: Any?): Boolean = repository.forever(key, value)

    fun forget(key: String): Boolean = repository.forget(key)

    fun flush(): Boolean = repository.flush()

    fun has(key: String): Boolean = repository.has(key)
}

/** Fake broadcaster using the log driver; records via [BroadcastManager.log]. */
class FakeBroadcast {
    val manager = BroadcastManager(
        Config().also {
            it.set("broadcasting.default", "log")
            it.set("broadcasting.connections.log.driver", "log")
        },
    )

    fun broadcast(event: ShouldBroadcast) = manager.broadcast(event)

    fun messages() = manager.log().messages()

    fun clear() = manager.log().clear()
}

/** In-memory filesystem fake. */
class FakeStorage : FilesystemAdapter {
    private val files = linkedMapOf<String, ByteArray>()
    private val dirs = linkedSetOf<String>()

    override fun put(path: String, contents: ByteArray) {
        files[path] = contents.copyOf()
    }

    override fun put(path: String, contents: String) {
        put(path, contents.toByteArray())
    }

    override fun get(path: String): ByteArray? = files[path]?.copyOf()

    override fun getString(path: String): String? = get(path)?.toString(Charsets.UTF_8)

    override fun exists(path: String): Boolean = files.containsKey(path) || dirs.contains(path)

    override fun delete(path: String): Boolean = files.remove(path) != null

    override fun copy(from: String, to: String): Boolean {
        val bytes = files[from] ?: return false
        files[to] = bytes.copyOf()
        return true
    }

    override fun move(from: String, to: String): Boolean {
        if (!copy(from, to)) return false
        delete(from)
        return true
    }

    override fun size(path: String): Long = files[path]?.size?.toLong() ?: 0L

    override fun lastModified(path: String): Long = if (exists(path)) System.currentTimeMillis() else 0L

    override fun files(directory: String): List<String> {
        val prefix = if (directory.isEmpty()) "" else directory.trimEnd('/') + "/"
        return files.keys.filter { it.startsWith(prefix) && !it.removePrefix(prefix).contains('/') }
            .map { it.removePrefix(prefix) }
    }

    override fun directories(directory: String): List<String> = dirs.filter {
        if (directory.isEmpty()) !it.contains('/') else it.startsWith(directory.trimEnd('/') + "/")
    }

    override fun makeDirectory(path: String): Boolean {
        dirs += path.trimEnd('/')
        return true
    }

    override fun deleteDirectory(path: String): Boolean {
        val prefix = path.trimEnd('/') + "/"
        files.keys.filter { it.startsWith(prefix) }.toList().forEach { files.remove(it) }
        dirs.remove(path.trimEnd('/'))
        return true
    }

    override fun url(path: String): String = "/$path"

    override fun path(path: String): String = path
}
