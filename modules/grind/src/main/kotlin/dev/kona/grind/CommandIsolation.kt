package dev.kona.grind

import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * Ensures only one process runs an isolatable command when `--isolated` is set.
 * Uses a file lock under `storage/framework/grind/` (no cache driver required).
 *
 * When the lock cannot be acquired, returns [ConsoleCommand.SUCCESS] without running.
 */
class CommandIsolation(
    private val basePath: Path,
    private val acquire: (FileChannel) -> FileLock?,
) {
    constructor(basePath: Path) : this(basePath, Companion::tryAcquire)

    fun runIsolated(commandName: String, block: () -> Int): Int {
        val dir = basePath.resolve("storage/framework/grind")
        Files.createDirectories(dir)
        val lockFile = dir.resolve(sanitize(commandName) + ".lock")
        FileChannel.open(
            lockFile,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
        ).use { channel ->
            val lock = acquire(channel) ?: return ConsoleCommand.SUCCESS
            try {
                return block()
            } finally {
                lock.release()
            }
        }
    }

    companion object {
        fun sanitize(name: String): String =
            name.replace(Regex("[^A-Za-z0-9._-]"), "_")

        fun tryAcquire(channel: FileChannel): FileLock? =
            try {
                channel.tryLock()
            } catch (_: OverlappingFileLockException) {
                null
            }
    }
}
