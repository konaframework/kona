package io.konaframework.testing

import io.konaframework.broadcasting.BroadcastManager
import io.konaframework.broadcasting.ShouldBroadcast
import io.konaframework.cache.ArrayCacheStore
import io.konaframework.cache.Repository
import io.konaframework.core.Config
import io.konaframework.events.EventDispatcher
import io.konaframework.filesystem.FilesystemAdapter
import io.konaframework.mail.LogTransport
import io.konaframework.mail.MailMessage
import io.konaframework.mail.Mailer
import io.konaframework.notifications.DatabaseChannel
import io.konaframework.notifications.LogChannel
import io.konaframework.notifications.Notifiable
import io.konaframework.notifications.Notification
import io.konaframework.notifications.NotificationSender
import io.konaframework.queue.Bus
import io.konaframework.queue.Job
import io.konaframework.queue.QueueManager
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
    private val visibility = linkedMapOf<String, String>()

    override fun put(path: String, contents: ByteArray) {
        files[path] = contents.copyOf()
    }

    override fun put(path: String, contents: String) {
        put(path, contents.toByteArray())
    }

    override fun get(path: String): ByteArray? = files[path]?.copyOf()

    override fun getString(path: String): String? = get(path)?.toString(Charsets.UTF_8)

    override fun exists(path: String): Boolean = files.containsKey(path) || dirs.contains(path)

    override fun missing(path: String): Boolean = !exists(path)

    override fun delete(path: String): Boolean {
        visibility.remove(path)
        return files.remove(path) != null
    }

    override fun copy(from: String, to: String): Boolean {
        val bytes = files[from] ?: return false
        files[to] = bytes.copyOf()
        visibility[from]?.let { visibility[to] = it }
        return true
    }

    override fun move(from: String, to: String): Boolean {
        if (!copy(from, to)) return false
        delete(from)
        return true
    }

    override fun size(path: String): Long = files[path]?.size?.toLong() ?: 0L

    override fun lastModified(path: String): Long = if (exists(path)) System.currentTimeMillis() else 0L

    override fun mimeType(path: String): String? =
        if (files.containsKey(path)) "application/octet-stream" else null

    override fun checksum(path: String, algorithm: String): String? {
        val bytes = files[path] ?: return null
        val digest = java.security.MessageDigest.getInstance(algorithm).digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    override fun files(directory: String): List<String> {
        val prefix = if (directory.isEmpty()) "" else directory.trimEnd('/') + "/"
        return files.keys.filter { it.startsWith(prefix) && !it.removePrefix(prefix).contains('/') }
            .map { it.removePrefix(prefix) }
    }

    override fun allFiles(directory: String): List<String> {
        val prefix = if (directory.isEmpty()) "" else directory.trimEnd('/') + "/"
        return files.keys.filter { it.startsWith(prefix) }.sorted()
    }

    override fun directories(directory: String): List<String> = dirs.filter {
        if (directory.isEmpty()) !it.contains('/') else it.startsWith(directory.trimEnd('/') + "/")
    }

    override fun allDirectories(directory: String): List<String> {
        val prefix = if (directory.isEmpty()) "" else directory.trimEnd('/') + "/"
        return dirs.filter { if (prefix.isEmpty()) true else it.startsWith(prefix) }.sorted()
    }

    override fun makeDirectory(path: String): Boolean {
        dirs += path.trimEnd('/')
        return true
    }

    override fun deleteDirectory(path: String): Boolean {
        val prefix = path.trimEnd('/') + "/"
        files.keys.filter { it.startsWith(prefix) }.toList().forEach {
            files.remove(it)
            visibility.remove(it)
        }
        dirs.remove(path.trimEnd('/'))
        return true
    }

    override fun url(path: String): String = "/$path"

    override fun path(path: String): String = path

    override fun append(path: String, data: String) {
        val existing = files[path] ?: ByteArray(0)
        put(path, existing + data.toByteArray())
    }

    override fun prepend(path: String, data: String) {
        val existing = files[path] ?: ByteArray(0)
        put(path, data.toByteArray() + existing)
    }

    override fun readStream(path: String): java.io.InputStream? =
        files[path]?.let { java.io.ByteArrayInputStream(it.copyOf()) }

    override fun writeStream(path: String, input: java.io.InputStream) {
        put(path, input.readAllBytes())
    }

    override fun putFile(directory: String, source: java.nio.file.Path): String {
        val name = java.util.UUID.randomUUID().toString()
        return putFileAs(directory, source, name)
    }

    override fun putFileAs(directory: String, source: java.nio.file.Path, name: String): String {
        val relative = if (directory.isEmpty()) name else "${directory.trimEnd('/')}/$name"
        put(relative, java.nio.file.Files.readAllBytes(source))
        return relative
    }

    override fun getVisibility(path: String): String = visibility[path] ?: "private"

    override fun setVisibility(path: String, visibility: String) {
        this.visibility[path] = visibility
    }

    override fun providesTemporaryUrls(): Boolean = false

    override fun temporaryUrl(path: String, expirationSeconds: Long): String =
        throw IllegalStateException("FakeStorage does not support temporary URLs.")
}
