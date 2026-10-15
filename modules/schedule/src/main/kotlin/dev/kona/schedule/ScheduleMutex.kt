package dev.kona.schedule

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.ConcurrentHashMap

/** Mutex for withoutOverlapping / onOneServer. */
class ScheduleMutex(
    private val basePath: Path = Path.of("").toAbsolutePath().normalize(),
) {
    private val local = ConcurrentHashMap<String, Long>()

    fun acquire(key: String, expiresSeconds: Long, onOneServer: Boolean): Boolean {
        val now = System.currentTimeMillis()
        expireLocal(now)
        if (onOneServer) {
            return acquireFile(key, expiresSeconds, now)
        }
        val expires = now + expiresSeconds.coerceAtLeast(1) * 1000
        val existing = local.putIfAbsent(key, expires)
        return existing == null
    }

    fun release(key: String, onOneServer: Boolean) {
        local.remove(key)
        if (onOneServer) {
            try {
                Files.deleteIfExists(lockPath(key))
            } catch (_: Exception) {
                // best-effort unlock
            }
        }
    }

    private fun acquireFile(key: String, expiresSeconds: Long, now: Long): Boolean {
        val path = lockPath(key)
        Files.createDirectories(path.parent)
        if (Files.exists(path)) {
            val expiresAt = Files.readString(path).trim().toLongOrNull() ?: 0L
            if (expiresAt > now) {
                return false
            }
        }
        val expires = now + expiresSeconds.coerceAtLeast(1) * 1000
        Files.writeString(
            path,
            expires.toString(),
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
        )
        local[key] = expires
        return true
    }

    private fun lockPath(key: String): Path {
        val safe = key.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return basePath.resolve("storage").resolve("framework").resolve("schedule-$safe.lock")
    }

    private fun expireLocal(now: Long) {
        local.entries.removeIf { it.value <= now }
    }
}
