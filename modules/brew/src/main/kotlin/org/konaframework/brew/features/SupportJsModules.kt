package org.konaframework.brew.features

import org.konaframework.brew.Component
import org.konaframework.brew.lifecycle.ComponentContext
import org.konaframework.brew.lifecycle.ComponentHook

fun setBrewJsModule(component: Component, source: String) {
    component.state().store["brew.js.module"] = source
}

fun loadBrewMultiFile(component: Component, classLoader: ClassLoader = Thread.currentThread().contextClassLoader) {
    val base = "brew/" + component.name().replace('.', '/')
    classLoader.getResourceAsStream("$base.js")?.use {
        setBrewJsModule(component, it.readBytes().toString(Charsets.UTF_8))
    }
    classLoader.getResourceAsStream("$base.css")?.use {
        setBrewScopedStyles(component, it.readBytes().toString(Charsets.UTF_8))
    }
    classLoader.getResourceAsStream("$base.global.css")?.use {
        setBrewGlobalStyles(component, it.readBytes().toString(Charsets.UTF_8))
    }
}

/**
 * Component JS modules (`SupportJsModules` / multi-file `name.js`).
 * Script source is emitted on `effects.scripts` for the client to evaluate with `$wire`.
 */
class SupportJsModules : ComponentHook() {
    override fun dehydrate(context: ComponentContext) {
        val source = component.state().store["brew.js.module"] as? String ?: return
        if (source.isBlank()) return
        context.pushEffect("scripts", linkedMapOf("name" to component.name(), "source" to source))
    }

    companion object {
        @JvmStatic
        fun setModule(component: Component, source: String) = setBrewJsModule(component, source)

        @JvmStatic
        fun loadMultiFile(component: Component, classLoader: ClassLoader = Thread.currentThread().contextClassLoader) =
            loadBrewMultiFile(component, classLoader)
    }
}
