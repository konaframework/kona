package org.konaframework.wire.features

import org.konaframework.wire.On
import org.konaframework.wire.lifecycle.ComponentContext
import org.konaframework.wire.lifecycle.ComponentHook
import org.konaframework.wire.meta.ComponentMeta

/**
 * Detects `@On("echo:…")` / `echo-private:` / `echo-presence:` listeners and
 * exposes them on `effects.listeners` so the JS runtime can wire Laravel Echo.
 */
class SupportEcho : ComponentHook() {
    override fun dehydrate(context: ComponentContext) {
        if (!context.mounting) return
        val echo = linkedMapOf<String, String>()
        for ((methodName, methods) in ComponentMeta.of(component).actions) {
            for (m in methods) {
                for (ann in m.getAnnotationsByType(On::class.java)) {
                    for (event in ann.value) {
                        if (isEcho(event)) echo[event] = methodName
                    }
                }
            }
        }
        for ((event, method) in component.listeners()) {
            if (isEcho(event)) echo[event] = method
        }
        if (echo.isNotEmpty()) context.addEffect("echo", echo)
    }

    companion object {
        fun isEcho(event: String): Boolean =
            event.startsWith("echo:") || event.startsWith("echo-private:") || event.startsWith("echo-presence:")
    }
}
