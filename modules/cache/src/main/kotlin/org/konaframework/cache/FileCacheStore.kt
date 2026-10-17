package org.konaframework.cache

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.util.stream.Collectors

class FileCacheStore @JvmOverloads constructor(
    private val directory: Path,
    private val clock: Clock = Clock.systemUTC(),
    private val mapper: ObjectMapper = jacksonObjectMapper(),
) : CacheStore {
    init {
        Files.createDirectories(directory)
    }

    override fun get(key: String): Any? {
        val path = pathFor(key)
        if (!Files.isRegularFile(path)) {
            return null
        }
        val node = mapper.readTree(Files.readAllBytes(path))
        val expiresNode = node.get("expires")
        if (expiresNode != null && !expiresNode.isNull) {
            val expiresText = expiresNode.asText()
            if (expiresText.isNotEmpty()) {
                val expires = Instant.parse(expiresText)
                if (!expires.isAfter(Instant.now(clock))) {
                    Files.deleteIfExists(path)
                    return null
                }
            }
        }
        val valueNode = node.get("value") ?: return null
        return mapper.treeToValue(valueNode, Any::class.java)
    }

    override fun put(key: String, value: Any?, seconds: Long) {
        val expires = if (seconds <= 0) {
            Instant.now(clock).toString()
        } else {
            Instant.now(clock).plusSeconds(seconds).toString()
        }
        write(key, value, expires)
    }

    override fun forever(key: String, value: Any?) {
        write(key, value, null)
    }

    override fun forget(key: String): Boolean = Files.deleteIfExists(pathFor(key))

    override fun flush() {
        if (!Files.isDirectory(directory)) {
            return
        }
        Files.list(directory).use { stream ->
            for (path in stream.collect(Collectors.toList())) {
                Files.deleteIfExists(path)
            }
        }
    }

    private fun write(key: String, value: Any?, expires: String?) {
        val root = mapper.createObjectNode()
        if (expires != null) {
            root.put("expires", expires)
        } else {
            root.putNull("expires")
        }
        root.set<com.fasterxml.jackson.databind.JsonNode>("value", mapper.valueToTree(value))
        Files.write(pathFor(key), mapper.writeValueAsBytes(root))
    }

    private fun pathFor(key: String): Path {
        val safe = key.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return directory.resolve("$safe.json")
    }
}
