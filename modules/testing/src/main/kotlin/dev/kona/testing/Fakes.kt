package dev.kona.testing

import dev.kona.core.Config
import dev.kona.filesystem.FilesystemAdapter
import dev.kona.mail.LogTransport
import dev.kona.mail.MailMessage
import dev.kona.mail.Mailer
import dev.kona.queue.Bus
import dev.kona.queue.Job
import dev.kona.queue.QueueManager

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
