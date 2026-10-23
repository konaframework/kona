package org.konaframework.wire

import org.konaframework.auth.authorization.AuthorizationException
import org.konaframework.wire.features.SupportAuthorize
import org.konaframework.wire.features.SupportDownloads
import org.konaframework.wire.hydration.Coercion
import org.konaframework.wire.lifecycle.HookSet
import org.konaframework.wire.meta.ComponentMeta
import org.konaframework.http.ValidationException
import org.konaframework.http.Validator

/**
 * A Wire component (`Livewire\Component`).
 *
 * Public state is every public Kotlin `var`/`val` with a backing field (or Java public field) declared on your subclass.
 * Public methods are actions callable from the browser; lifecycle hooks (`mount`, `boot`, `updatedFoo`, …) are found
 * by name. Render with [render] (return a [WireView], raw HTML, or `null`), or override [template] with inline Crema.
 */
abstract class Component {
    private var state: ComponentState? = null

    @JvmSynthetic
    internal fun attach(state: ComponentState) {
        this.state = state
    }

    @JvmSynthetic
    internal fun state(): ComponentState = state ?: throw WireException(
        "Component [${javaClass.name}] is not attached to Wire; create it through Wire.mount / Wire.test",
    )

    /** Unique component id (`$this->getId()`). */
    fun id(): String = state().id

    /** Registered component name (`$this->getName()`). */
    fun name(): String = state().name

    /** Rendering hook: return a [WireView] / HTML string, or `null` to use [template] or the convention view. */
    open fun render(): Any? = null

    /**
     * Inline Crema source for single-file components. Kotlin raw strings: write `{{ count }}`
     * (the `$` is optional).
     *
     * For IDE highlighting, put `// language=Crema` on the line above the string (IntelliJ /
     * Kona Idea), or keep markup in `views/wire/<name>.crema.html`. See [CremaTemplate].
     */
    @CremaTemplate
    open fun template(): String? = null

    /** Crema view `name` with optional [data] (`view('livewire.x', [...])`). */
    @JvmOverloads
    protected fun view(name: String, data: Map<String, Any?> = emptyMap()): WireView = WireView.named(name, data)

    /** Inline Crema [source] with optional [data]. */
    @JvmOverloads
    protected fun inline(@CremaTemplate source: String, data: Map<String, Any?> = emptyMap()): WireView =
        WireView.inline(source, data)

    /** Skip rendering for this request (`$this->skipRender()`); optional replacement [html]. Ignored after [forceRender]. */
    @JvmOverloads
    fun skipRender(html: String? = null) {
        val state = state()
        if (state.forceRender) return
        state.skipRender = true
        state.skipRenderHtml = html
    }

    /** Alias of [skipRender] (`$this->renderless()`). */
    fun renderless() = skipRender()

    /** Make later [skipRender] calls (including renderless actions) no-ops for this request (`$this->forceRender()`). */
    fun forceRender() {
        state().forceRender = true
    }

    /** Hint a typed View Transition for this request (`$this->transition('forward')`). */
    @JvmOverloads
    fun transition(type: String? = null, skip: Boolean = false) {
        if (type != null) state().store["transitionType"] = type
        if (skip) state().store["transitionSkip"] = true
    }

    /** Skip View Transitions for this request (`$this->skipTransition()`). */
    fun skipTransition() {
        state().store["transitionSkip"] = true
    }

    /** Assign several properties (`fill`). Unknown keys are ignored. */
    fun fill(values: Map<String, Any?>) {
        val meta = ComponentMeta.of(this)
        for ((key, value) in values) {
            val prop = meta.properties[key] ?: continue
            prop.set(this, Coercion.toType(value, prop.type, prop.genericType))
        }
    }

    /** Reset [properties] (all when empty) to their initial values (`reset`). */
    fun reset(vararg properties: String) {
        val fresh = freshInstance()
        val meta = ComponentMeta.of(this)
        val names = if (properties.isEmpty()) meta.properties.keys else properties.toList()
        for (name in names) {
            val prop = meta.properties[name] ?: throw PublicPropertyNotFoundException(name, nameOrClass())
            prop.set(this, prop.get(fresh))
        }
    }

    /** Reset every property except [properties] (`resetExcept`). */
    fun resetExcept(vararg properties: String) {
        val keep = properties.toSet()
        val names = ComponentMeta.of(this).properties.keys.filter { it !in keep }
        if (names.isNotEmpty()) reset(*names.toTypedArray())
    }

    /** Return a property's value and reset it (`pull('title')`). */
    fun pull(property: String): Any? {
        val value = all()[property]
        reset(property)
        return value
    }

    /** Return several properties and reset them (`pull(['a', 'b'])`; all when empty). */
    fun pull(vararg properties: String): Map<String, Any?> {
        val values = if (properties.isEmpty()) all() else only(*properties)
        reset(*properties)
        return values
    }

    /** Public state for [properties] (`only`). */
    fun only(vararg properties: String): Map<String, Any?> {
        val all = all()
        return properties.filter { all.containsKey(it) }.associateWithTo(LinkedHashMap()) { all[it] }
    }

    /** All public state (`all()`). */
    fun all(): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        for ((name, prop) in ComponentMeta.of(this).properties) out[name] = prop.get(this)
        return out
    }

    /** Memoized value of the `@Computed` method [name] for this request. */
    @Suppress("UNCHECKED_CAST")
    fun <T> computed(name: String): T = state().manager.computedValue(this, name) as T

    /** Forget a memoized computed value (`unset($this->posts)`). */
    fun forgetComputed(name: String) {
        state().manager.forgetComputed(this, name)
    }

    /** Validation error bag (`getErrorBag()`). */
    fun errorBag(): ErrorBag = state().errorBag

    fun getErrorBag(): ErrorBag = errorBag()

    fun setErrorBag(bag: ErrorBag) {
        state().errorBag = bag
    }

    fun addError(key: String, message: String) {
        errorBag().add(key, message)
    }

    /** Authorize via Kona Gate (`$this->authorize('update', $post)`). */
    fun authorize(ability: String, vararg arguments: Any?) {
        val gate = SupportAuthorize.gateOrNull(state().manager)
            ?: throw AuthorizationException("No Gate bound; cannot authorize [$ability]")
        gate.authorize(ability, *arguments)
    }

    fun resetErrorBag() {
        errorBag().clear()
    }

    fun resetValidation(vararg fields: String) {
        if (fields.isEmpty()) errorBag().clear() else fields.forEach { errorBag().clear(it) }
    }

    /** Validate public properties (`$this->validate()`). */
    fun validate(): Map<String, Any?> = validate(null)

    /**
     * Validate public properties (`$this->validate([...])`).
     * [rules] overrides annotation / `rules()` maps when non-null.
     */
    fun validate(rules: Map<String, List<String>>?): Map<String, Any?> {
        val resolved = rules ?: ValidationRules.forComponent(this)
        val data = all()
        val validator = Validator(data, resolved)
        try {
            val validated = validator.validate()
            errorBag().clear()
            return validated
        } catch (e: ValidationException) {
            errorBag().clear().merge(e.errors)
            throw e
        }
    }

    fun validateOnly(field: String): Map<String, Any?> {
        val rules = ValidationRules.forComponent(this).filterKeys { it == field || it.startsWith("$field.") }
        return validate(rules)
    }

    /** Optional component-level rules map (`rules()`). */
    open fun rules(): Map<String, List<String>> = emptyMap()

    open fun messages(): Map<String, String> = emptyMap()

    open fun validationAttributes(): Map<String, String> = emptyMap()

    /** Event listeners map (`getListeners()` / `$listeners`). Keys are event names; values are method names. */
    open fun listeners(): Map<String, String> = emptyMap()

    /** Queue a browser event (`$this->dispatch('name', key to value)`). */
    fun dispatch(name: String, params: Map<String, Any?> = emptyMap()): Event {
        val event = Event(name, params)
        @Suppress("UNCHECKED_CAST")
        val list = state().store.getOrPut("dispatched") { mutableListOf<Event>() } as MutableList<Event>
        list += event
        org.konaframework.wire.features.SupportNesting.notifyParent(this, name, params)
        return event
    }

    /** Kotlin named-params style: `dispatch("saved", "id" to 1)`. */
    fun dispatch(name: String, vararg params: Pair<String, Any?>): Event =
        dispatch(name, params.toMap())

    /** Evaluate JS in the browser after morph (`$this->js('alert(1)')`). */
    fun js(expression: String) {
        @Suppress("UNCHECKED_CAST")
        val list = state().store.getOrPut("xjs") { mutableListOf<String>() } as MutableList<String>
        list += expression
    }

    /** Redirect the browser (`$this->redirect(url)`). */
    @JvmOverloads
    fun redirect(url: String, navigate: Boolean = false) {
        state().store["redirect"] = linkedMapOf("url" to url, "navigate" to navigate)
        skipRender()
    }

    @JvmOverloads
    fun redirectRoute(name: String, parameters: Map<String, Any?> = emptyMap(), navigate: Boolean = false) {
        state().store["redirect"] = linkedMapOf("route" to name, "parameters" to parameters, "navigate" to navigate)
        skipRender()
    }

    @JvmOverloads
    fun redirectIntended(default: String = "/", navigate: Boolean = false) {
        state().store["redirect"] = linkedMapOf("intended" to true, "default" to default, "navigate" to navigate)
        skipRender()
    }

    /** Stream HTML/text into a `wire:stream` target (`$this->stream(...)`). */
    @JvmOverloads
    fun stream(content: String, to: String? = null, replace: Boolean = false) {
        @Suppress("UNCHECKED_CAST")
        val list = state().store.getOrPut("streams") { mutableListOf<Map<String, Any?>>() } as MutableList<Map<String, Any?>>
        list += linkedMapOf("content" to content, "to" to to, "replace" to replace)
    }

    /** Redirect to a named controller action (`$this->redirectAction(...)`). */
    @JvmOverloads
    fun redirectAction(action: String, parameters: Map<String, Any?> = emptyMap(), navigate: Boolean = false) {
        state().store["redirect"] = linkedMapOf("action" to action, "parameters" to parameters, "navigate" to navigate)
        skipRender()
    }

    /** Flash a session value that survives the next redirect (`session()->flash(...)`). */
    fun flash(key: String, value: Any?) {
        @Suppress("UNCHECKED_CAST")
        val bag = state().store.getOrPut("flash") { linkedMapOf<String, Any?>() } as MutableMap<String, Any?>
        bag[key] = value
        val request = state().manager.currentRequest()
        if (request != null) {
            val session = request.session()
            if (session != null) session.flash(key, value)
        }
    }

    /** Queue a file download effect (`response()->download` parity). */
    @JvmOverloads
    fun download(contents: ByteArray, name: String, contentType: String = "application/octet-stream") {
        state().store["download"] = linkedMapOf(
            "name" to name,
            "contentType" to contentType,
            "content" to SupportDownloads.encode(contents),
        )
        skipRender()
    }

    /** Queue a streamed download from a string supplier. */
    @JvmOverloads
    fun streamDownload(contents: String, name: String, contentType: String = "application/octet-stream") {
        download(contents.toByteArray(Charsets.UTF_8), name, contentType)
    }

    /** Parent component when nested (`$this->parent()` / `$parent`). */
    fun parent(): Component? {
        (state().store["parent"] as? Component)?.let { return it }
        val stack = state().manager.currentStack()
        val idx = stack.indexOf(this)
        return if (idx > 0) stack[idx - 1] else null
    }

    /** Default lazy placeholder HTML (`placeholder()`). */
    open fun placeholder(): String? = null

    /** Disable bfcache for this full-page component (`disableBackButtonCache()`). */
    fun disableBackButtonCache() {
        state().store["disableBackButtonCache"] = true
    }

    /** Set layout for a full-page render (`$this->layout(...)`). */
    fun layout(name: String) {
        state().store["layout"] = name
    }

    /** Set document title (`$this->title(...)`). */
    fun title(value: String) {
        state().store["title"] = value
    }

    private fun nameOrClass(): String {
        val s = state ?: return javaClass.simpleName
        return s.name
    }

    private fun freshInstance(): Any {
        state?.let { return it.manager.freshInstance(this) }
        val ctor = javaClass.getDeclaredConstructor()
        ctor.isAccessible = true
        return ctor.newInstance()
    }
}

/** Runtime bookkeeping kept off the public surface. */
internal class ComponentState(
    val id: String,
    val name: String,
    val manager: WireManager,
) {
    lateinit var hooks: HookSet
    var skipRender: Boolean = false
    var forceRender: Boolean = false
    var skipRenderHtml: String? = null
    val computedMemo: MutableMap<String, Any?> = HashMap()
    val store: MutableMap<String, Any?> = HashMap()
    var errorBag: ErrorBag = ErrorBag()
    var renderedView: WireView? = null
    var renderedData: Map<String, Any?>? = null
}
