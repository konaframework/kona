package io.konaframework.queue

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.nio.file.Files
import java.nio.file.Path

/**
 * Persists failed jobs as a JSON array on disk so retries survive process restart.
 *
 * Config: `queue.failed.driver=file` and `queue.failed.path` (absolute or relative
 * to the application base path).
 */
class FileFailedJobProvider @JvmOverloads constructor(
    private val path: Path,
    private val mapper: ObjectMapper = jacksonObjectMapper(),
) : FailedJobProvider() {
    init {
        load()
    }

    override fun persist() {
        val parent = path.parent
        if (parent != null) {
            Files.createDirectories(parent)
        }
        val json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jobs.toList())
        Files.writeString(path, json)
    }

    private fun load() {
        if (!Files.exists(path) || Files.size(path) == 0L) {
            return
        }
        try {
            val loaded: List<FailedJob> = mapper.readValue(Files.readString(path))
            jobs.clear()
            jobs.addAll(loaded)
        } catch (_: Exception) {
            jobs.clear()
        }
    }
}
