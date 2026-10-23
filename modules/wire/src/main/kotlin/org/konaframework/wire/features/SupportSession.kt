package org.konaframework.wire.features

import org.konaframework.wire.Session
import org.konaframework.wire.lifecycle.ComponentContext
import org.konaframework.wire.lifecycle.ComponentHook
import org.konaframework.wire.meta.ComponentMeta
import org.konaframework.session.StartSession
import org.konaframework.session.Session as KonaSession

/** `@Session` properties — load on mount/hydrate, persist on dehydrate. */
class SupportSession : ComponentHook() {
    override fun mount(params: Map<String, Any?>, key: String?) {
        loadFromSession()
    }

    override fun hydrate(memo: Map<String, Any?>, context: ComponentContext) {
        loadFromSession()
    }

    override fun dehydrate(context: ComponentContext) {
        val session = session() ?: return
        val meta = ComponentMeta.of(component)
        for ((name, prop) in meta.properties) {
            val ann = prop.annotations.filterIsInstance<Session>().firstOrNull() ?: continue
            val key = resolveKey(ann.key.ifBlank { name })
            session.put(key, prop.get(component))
        }
    }

    private fun loadFromSession() {
        val session = session() ?: return
        val meta = ComponentMeta.of(component)
        for ((name, prop) in meta.properties) {
            val ann = prop.annotations.filterIsInstance<Session>().firstOrNull() ?: continue
            val key = resolveKey(ann.key.ifBlank { name })
            if (!session.has(key)) continue
            prop.set(component, session.get(key))
        }
    }

    private fun resolveKey(template: String): String {
        val meta = ComponentMeta.of(component)
        var resolved = template
        for (match in Regex("""\{([^}]+)\}""").findAll(template)) {
            val prop = meta.properties[match.groupValues[1]]
            val piece = if (prop == null) "" else (prop.get(component) ?: "").toString()
            resolved = resolved.replace(match.value, piece)
        }
        return "brew.session.$resolved"
    }

    private fun session(): KonaSession? =
        manager.currentRequest()?.getAttribute(StartSession.ATTRIBUTE) as? KonaSession
}
