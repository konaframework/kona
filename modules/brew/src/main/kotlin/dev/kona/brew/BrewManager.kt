package dev.kona.brew

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dev.kona.http.Request
import dev.kona.http.Response

/**
 * Registry + render + HTTP message handling for Brew components.
 */
class BrewManager(
    val registry: ComponentRegistry = ComponentRegistry(),
) {
    private val mapper: ObjectMapper = jacksonObjectMapper()

    @JvmSynthetic
    fun register(name: String, factory: () -> Component): BrewManager {
        registry.register(name, factory)
        return this
    }

    fun register(name: String, factory: java.util.function.Supplier<out Component>): BrewManager {
        registry.register(name, factory)
        return this
    }

    /** Initial HTML for embedding in a page or Crema layout. */
    fun mount(name: String): String {
        val component = registry.resolve(name)
        return BrewRenderer.render(name, component)
    }

    fun mount(name: String, configure: (Component) -> Unit): String {
        val component = registry.resolve(name)
        configure(component)
        return BrewRenderer.render(name, component)
    }

    /**
     * Restore from snapshot, optionally sync params into state, invoke [method], return HTML + snapshot.
     */
    @JvmOverloads
    fun update(
        name: String,
        snapshot: String,
        method: String,
        params: Map<String, Any?> = emptyMap(),
    ): UpdateResult {
        val payload = BrewSnapshot.decode(snapshot)
        val resolvedName = name.ifBlank { payload.name }
        if (resolvedName.isBlank()) {
            throw BrewException("Brew component name is required")
        }
        if (payload.name.isNotBlank() && payload.name != resolvedName) {
            throw BrewException("Snapshot name ${payload.name} does not match $resolvedName")
        }
        val component = registry.resolve(resolvedName)
        if (payload.id.isNotBlank()) {
            component.brewId = payload.id
        }
        BrewState.write(component, payload.data)
        // Params may include wire:model updates sent alongside an action.
        if (params.isNotEmpty() && method != "sync") {
            BrewState.write(component, params.filterKeys { it != "value" })
        }
        BrewActions.invoke(component, method, params)
        val html = BrewRenderer.render(
            resolvedName,
            component,
            assignId = component.brewId.isBlank(),
            callMount = false,
        )
        val nextSnapshot = BrewSnapshot.encode(resolvedName, component)
        return UpdateResult(html = html, snapshot = nextSnapshot, id = component.brewId)
    }

    /** `POST /brew/message` handler. */
    fun handleMessage(request: Request): Response {
        return try {
            val body = parseBody(request.body)
            val name = body.string("name")
            val snapshot = body.string("snapshot")
            val method = body.string("method")
            if (snapshot.isBlank()) {
                throw BrewException("Brew snapshot is required")
            }
            if (method.isBlank()) {
                throw BrewException("Brew action method is required")
            }
            @Suppress("UNCHECKED_CAST")
            val params = (body["params"] as? Map<String, Any?>) ?: emptyMap()
            val result = update(name, snapshot, method, params)
            Response.json(
                mapOf(
                    "html" to result.html,
                    "snapshot" to result.snapshot,
                    "id" to result.id,
                ),
            )
        } catch (ex: BrewException) {
            Response.json(mapOf("error" to ex.message), 422)
        }
    }

    fun scriptResponse(): Response =
        Response(
            status = 200,
            body = BrewAssets.javascript(),
            headers = linkedMapOf("Content-Type" to "application/javascript; charset=utf-8"),
        )

    private fun parseBody(raw: String): Map<String, Any?> {
        if (raw.isBlank()) {
            throw BrewException("Brew message body is required")
        }
        return try {
            mapper.readValue(raw)
        } catch (ex: Exception) {
            throw BrewException("Invalid Brew JSON body", ex)
        }
    }

    private fun Map<String, Any?>.string(key: String): String {
        val value = this[key] ?: return ""
        return value.toString()
    }

    data class UpdateResult(
        val html: String,
        val snapshot: String,
        val id: String,
    )
}
