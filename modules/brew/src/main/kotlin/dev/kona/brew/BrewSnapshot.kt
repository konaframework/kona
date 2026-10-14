package dev.kona.brew

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.util.Base64

/**
 * Encodes component name + public state as Base64 JSON for `data-brew`.
 */
object BrewSnapshot {
    private val mapper: ObjectMapper = jacksonObjectMapper()

    data class Payload(
        val name: String,
        val data: Map<String, Any?> = emptyMap(),
        val id: String = "",
    )

    @JvmStatic
    fun encode(name: String, component: Component): String {
        val payload = Payload(
            name = name,
            data = BrewState.read(component),
            id = component.brewId,
        )
        return Base64.getEncoder().encodeToString(mapper.writeValueAsBytes(payload))
    }

    @JvmStatic
    fun decode(encoded: String): Payload {
        if (encoded.isBlank()) {
            throw BrewException("Empty Brew snapshot")
        }
        return try {
            val json = Base64.getDecoder().decode(encoded)
            mapper.readValue(json)
        } catch (ex: Exception) {
            throw BrewException("Invalid Brew snapshot", ex)
        }
    }
}
