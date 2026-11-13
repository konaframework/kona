package org.konaframework.queue

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.util.function.BiConsumer
import java.util.function.Consumer

/**
 * Stores `then` / `catch` / `finally` callbacks on the batch row so another process can run them.
 *
 * A callback is stored when it is a concrete class with a public no-arg constructor that Jackson
 * can write. Lambdas and anonymous classes stay on the dispatching process.
 */
internal object BatchCallbacks {
    private val mapper: ObjectMapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)

    fun encode(
        thenCallback: Consumer<Batch>?,
        catchCallback: BiConsumer<Batch, Throwable?>?,
        finallyCallback: Consumer<Batch>?,
    ): String? {
        val thenPiece = piece(thenCallback)
        val catchPiece = piece(catchCallback)
        val finallyPiece = piece(finallyCallback)
        if (thenPiece == null && catchPiece == null && finallyPiece == null) {
            return null
        }
        val payload = LinkedHashMap<String, Map<String, String>>()
        if (thenPiece != null) {
            payload["then"] = thenPiece
        }
        if (catchPiece != null) {
            payload["catch"] = catchPiece
        }
        if (finallyPiece != null) {
            payload["finally"] = finallyPiece
        }
        return mapper.writeValueAsString(payload)
    }

    fun attach(batch: Batch, row: Map<String, Any?>) {
        val raw = row.entries.firstOrNull { it.key.equals("callbacks", ignoreCase = true) }?.value
        if (raw == null) {
            return
        }
        val text = raw.toString()
        if (text.isBlank()) {
            return
        }
        val tree = try {
            @Suppress("UNCHECKED_CAST")
            mapper.readValue(text, Map::class.java) as Map<*, *>
        } catch (_: Exception) {
            return
        }
        batch.attachCallbacks(
            readConsumer(tree, "then"),
            readBiConsumer(tree, "catch"),
            readConsumer(tree, "finally"),
        )
    }

    private fun piece(callback: Any?): Map<String, String>? {
        if (callback == null) {
            return null
        }
        val type = callback.javaClass
        if (!persistable(type)) {
            return null
        }
        return try {
            mapOf(
                "className" to type.name,
                "body" to mapper.writeValueAsString(callback),
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun persistable(type: Class<*>): Boolean {
        if (type.isAnonymousClass) {
            return false
        }
        if (type.isLocalClass) {
            return false
        }
        if (type.isSynthetic) {
            return false
        }
        return try {
            type.getDeclaredConstructor()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun readConsumer(tree: Map<*, *>, key: String): Consumer<Batch>? {
        val created = instantiate(tree, key) ?: return null
        if (created is Consumer<*>) {
            @Suppress("UNCHECKED_CAST")
            return created as Consumer<Batch>
        }
        return null
    }

    private fun readBiConsumer(tree: Map<*, *>, key: String): BiConsumer<Batch, Throwable?>? {
        val created = instantiate(tree, key) ?: return null
        if (created is BiConsumer<*, *>) {
            @Suppress("UNCHECKED_CAST")
            return created as BiConsumer<Batch, Throwable?>
        }
        return null
    }

    private fun instantiate(tree: Map<*, *>, key: String): Any? {
        val piece = tree[key] as? Map<*, *> ?: return null
        val className = piece["className"]?.toString()
        if (className.isNullOrBlank()) {
            return null
        }
        val body = piece["body"]?.toString()
        if (body.isNullOrBlank()) {
            return null
        }
        return try {
            val type = Class.forName(className)
            mapper.readValue(body, type)
        } catch (_: Exception) {
            null
        }
    }
}
