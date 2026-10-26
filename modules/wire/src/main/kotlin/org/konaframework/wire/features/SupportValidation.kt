package org.konaframework.wire.features

import org.konaframework.wire.Component
import org.konaframework.wire.ErrorBag
import org.konaframework.wire.Form
import org.konaframework.wire.ValidationRules
import org.konaframework.wire.WireException
import org.konaframework.wire.lifecycle.ComponentContext
import org.konaframework.wire.lifecycle.ComponentHook
import org.konaframework.wire.lifecycle.StopPropagation
import org.konaframework.wire.meta.ComponentMeta
import org.konaframework.http.ValidationException
import java.lang.reflect.Modifier

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
        val realtime = {
            if (ValidationRules.shouldValidateOnUpdate(component, property)) {
                runCatching { component.validateOnly(property) }
            }
        }
        val meta = ComponentMeta.of(component).properties[property] ?: return realtime
        val form = meta.get(component) as? Form ?: return realtime
        if (!fullPath.contains('.')) {
            return {
                val current = meta.get(component)
                if (current is Form) current.attach(component, property)
            }
        }
        val local = fullPath.substringAfter('.')
        form.updating(local, value)
        return {
            val current = meta.get(component) as Form
            current.attach(component, property)
            current.updated(local, value)
            if (current.shouldValidateOnUpdate(local.substringBefore('.'))) {
                runCatching { current.validateOnly(local) }
            }
        }
    }

    override fun dehydrate(context: ComponentContext) {
        forEachForm { it.dehydrate() }
        val bag = component.errorBag()
        val wire = bag.toWire()
        context.addMemo("errors", if (wire.isEmpty()) emptyList<Any?>() else wire)
        if (wire.isNotEmpty()) context.addEffect("errors", wire)
    }

    override fun render(view: org.konaframework.wire.WireView, data: MutableMap<String, Any?>): ((String) -> String?)? {
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
            var value = prop.get(component)
            if (value == null && !prop.readOnly && concreteForm(prop.type)) {
                value = try {
                    prop.type.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
                } catch (e: ReflectiveOperationException) {
                    throw WireException(
                        "Form [${prop.type.simpleName}] on ${component.javaClass.simpleName}.$name requires a public no-arg constructor",
                        e,
                    )
                }
                prop.set(component, value)
            }
            if (value is Form) value.attach(component, name)
        }
    }

    private fun concreteForm(type: Class<*>): Boolean =
        Form::class.java.isAssignableFrom(type) && !Modifier.isAbstract(type.modifiers)

    private fun forEachForm(block: (Form) -> Unit) {
        for ((_, prop) in ComponentMeta.of(component).properties) {
            val value = prop.get(component) ?: continue
            if (value is Form) block(value)
        }
    }
}
