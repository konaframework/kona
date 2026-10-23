package org.konaframework.wire.features

import org.konaframework.auth.authorization.AuthorizationException
import org.konaframework.auth.authorization.Gate
import org.konaframework.wire.Authorize
import org.konaframework.wire.meta.ComponentMeta
import org.konaframework.wire.lifecycle.ComponentContext
import org.konaframework.wire.lifecycle.ComponentHook
import org.konaframework.wire.lifecycle.ReturnEarly

/** Enforces `@Authorize` on actions before they run (`SupportAuthorization`). */
class SupportAuthorize : ComponentHook() {
    override fun call(
        method: String,
        params: List<Any?>,
        returnEarly: ReturnEarly,
        metadata: Map<String, Any?>,
        context: ComponentContext,
    ): ((Any?) -> Any?)? {
        val candidates = ComponentMeta.of(component).actions[method] ?: return null
        val target = candidates.firstOrNull { it.parameterCount == params.size }
            ?: candidates.maxBy { it.parameterCount }
        val annotation = target.getAnnotation(Authorize::class.java) ?: return null
        authorize(annotation.ability, *resolveArgs(annotation.arguments))
        return null
    }

    private fun resolveArgs(arguments: Array<out String>): Array<Any?> {
        val props = ComponentMeta.of(component).properties
        return arguments.map { arg ->
            props[arg]?.get(component) ?: arg
        }.toTypedArray()
    }

    companion object {
        /** Resolve Gate from the Wire container when present. */
        fun gateOrNull(manager: org.konaframework.wire.WireManager): Gate? {
            val container = manager.container ?: return null
            return try {
                if (container.bound(Gate::class.java)) container.make(Gate::class.java) else null
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun authorize(ability: String, vararg arguments: Any?) {
        val gate = gateOrNull(manager)
            ?: throw AuthorizationException("No Gate bound; cannot authorize [$ability]")
        gate.authorize(ability, *arguments)
    }
}
