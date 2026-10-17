package org.konaframework.session

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * File session driver under [directory] (default `storage/framework/sessions`).
 * Each session is a JSON file named by id; expired files are deleted on read.
 */
class FileSessionStore @JvmOverloads constructor(
    private val directory: Path,
    private val mapper: ObjectMapper = jacksonObjectMapper(),
) : SessionStore {
    init {
        Files.createDirectories(directory)
    }

    override fun read(id: String): Map<String, Any?> {
        if (!isValidId(id)) {
            return emptyMap()
        }
        val file = directory.resolve(id)
        if (!Files.isRegularFile(file)) {
            return emptyMap()
        }
        return try {
            @Suppress("UNCHECKED_CAST")
            val envelope = mapper.readValue(file.toFile(), Map::class.java) as Map<String, Any?>
            val expiresAt = (envelope["expires_at"] as? Number)?.toLong()
            if (expiresAt != null && expiresAt < System.currentTimeMillis()) {
                destroy(id)
                return emptyMap()
            }
            @Suppress("UNCHECKED_CAST")
            val data = envelope["data"] as? Map<String, Any?> ?: emptyMap()
            LinkedHashMap(data)
        } catch (_: Exception) {
            destroy(id)
            emptyMap()
        }
    }

    override fun write(id: String, data: Map<String, Any?>, lifetimeMinutes: Int) {
        if (!isValidId(id)) {
            return
        }
        Files.createDirectories(directory)
        val expiresAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(lifetimeMinutes.toLong().coerceAtLeast(0))
        val envelope = mapOf(
            "expires_at" to expiresAt,
            "data" to data,
        )
        Files.writeString(directory.resolve(id), mapper.writeValueAsString(envelope))
    }

    override fun destroy(id: String) {
        if (!isValidId(id)) {
            return
        }
        Files.deleteIfExists(directory.resolve(id))
    }

    companion object {
        private val ID = Regex("^[a-zA-Z0-9]{40}$")

        @JvmStatic
        fun isValidId(id: String): Boolean = ID.matches(id)
    }
}
