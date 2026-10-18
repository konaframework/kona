package org.konaframework.brew.features

import org.konaframework.brew.Component
import org.konaframework.brew.ErrorBag
import org.konaframework.brew.Form
import org.konaframework.brew.ValidationRules
import org.konaframework.brew.lifecycle.ComponentContext
import org.konaframework.brew.lifecycle.ComponentHook
import org.konaframework.brew.lifecycle.StopPropagation
import org.konaframework.brew.meta.ComponentMeta
import org.konaframework.http.ValidationException

/**
 * Form attach, real-time `@Validate` on update, ValidationException → error bag, memo.errors + view `errors`.
 */
class SupportValidation : ComponentHook() {
    override fun mount(params: Map<String, Any?>, key: String?) {
        attachForms(component)
        ValidationRules.forComponent(component)
        forEachForm { it.boot() }
    }

    override fun hydrate(memo: Map<String, Any?>, context: ComponentContext) {
        attachForms(component)
        ValidationRules.forComponent(component)
        val rawErrors = memo["errors"]
        if (rawErrors is Map<*, *>) {
            component.setErrorBag(ErrorBag.fromWire(rawErrors))
        }
        forEachForm {
            it.boot()
            it.hydrate()
        }
    }

    override fun update(property: String, fullPath: String, value: Any?): (() -> Unit) {
        val form = ComponentMeta.of(component).properties[property]?.get(component) as? Form
        if (form == null) {
            return {
                if (ValidationRules.shouldValidateOnUpdate(component, property)) {
                    runCatching { component.validateOnly(property) }
                }
            }
        }
        if (!fullPath.contains('.')) {
            return {}
        }
        val local = fullPath.substringAfter('.')
        form.updating(local, value)
        return {
            form.updated(local, value)
            runCatching { form.validateOnly(local) }
        }
    }

    override fun dehydrate(context: ComponentContext) {
        forEachForm { it.dehydrate() }
        val bag = component.errorBag()
        val wire = bag.toWire()
        context.addMemo("errors", if (wire.isEmpty()) emptyList<Any?>() else wire)
        if (wire.isNotEmpty()) context.addEffect("errors", wire)
    }

    override fun render(view: org.konaframework.brew.BrewView, data: MutableMap<String, Any?>): ((String) -> String?)? {
        data["errors"] = component.errorBag().toWire()
        return null
    }

    override fun exception(error: Throwable, stopPropagation: StopPropagation) {
        if (error is ValidationException) {
            component.errorBag().clear().merge(error.errors)
            stopPropagation()
        }
    }

    private fun attachForms(component: Component) {
        for ((name, prop) in ComponentMeta.of(component).properties) {
            val value = prop.get(component) ?: continue
            if (value is Form) value.attach(component, name)
        }
    }

    private fun forEachForm(block: (Form) -> Unit) {
        for ((_, prop) in ComponentMeta.of(component).properties) {
            val value = prop.get(component) ?: continue
            if (value is Form) block(value)
        }
    }
}
