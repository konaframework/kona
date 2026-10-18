package org.konaframework.brew

import org.konaframework.brew.features.ComputedCache
import org.konaframework.brew.features.InMemoryComputedCache
import org.konaframework.brew.features.SupportComputed
import org.konaframework.brew.features.SupportLifecycleHooks
import org.konaframework.brew.features.SupportLockedProperties
import org.konaframework.brew.features.SupportMagicActions
import org.konaframework.brew.features.SupportDownloads
import org.konaframework.brew.features.SupportEcho
import org.konaframework.brew.features.SupportEvents
import org.konaframework.brew.features.SupportIslands
import org.konaframework.brew.features.SupportJsEvaluation
import org.konaframework.brew.features.SupportLazyLoading
import org.konaframework.brew.features.SupportNesting
import org.konaframework.brew.features.SupportPages
import org.konaframework.brew.features.SupportPagination
import org.konaframework.brew.features.SupportQueryString
import org.konaframework.brew.features.SupportRedirects
import org.konaframework.brew.features.SupportScripts
import org.konaframework.brew.features.SupportSession
import org.konaframework.brew.features.SupportSlots
import org.konaframework.brew.features.SupportStreaming
import org.konaframework.brew.features.SupportTransitions
import org.konaframework.brew.features.SupportAuthorize
import org.konaframework.brew.features.SupportFileUploads
import org.konaframework.brew.features.SupportJsModules
import org.konaframework.brew.features.SupportLocales
import org.konaframework.brew.features.SupportReleaseTokens
import org.konaframework.brew.features.SupportStyles
import org.konaframework.brew.features.SupportValidation
import org.konaframework.brew.hydration.Checksum
import org.konaframework.brew.hydration.Coercion
import org.konaframework.brew.hydration.ModelCollectionSynth
import org.konaframework.brew.hydration.ModelSynth
import org.konaframework.brew.hydration.Synth
import org.konaframework.brew.hydration.Synthesizers
import org.konaframework.brew.uploads.SignedUpload
import org.konaframework.http.Middleware
import java.nio.file.Files
import java.nio.file.Path
import java.util.HexFormat
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.konaframework.brew.lifecycle.ComponentContext
import org.konaframework.brew.lifecycle.ComponentHook
import org.konaframework.brew.lifecycle.HookSet
import org.konaframework.brew.lifecycle.ReturnEarly
import org.konaframework.brew.meta.ComponentMeta
import org.konaframework.brew.meta.Invoker
import org.konaframework.brew.view.HtmlRoot
import org.konaframework.core.Container
import org.konaframework.crema.CremaContext
import org.konaframework.crema.CremaEngine
import org.konaframework.http.Handler
import org.konaframework.http.Request
import org.konaframework.http.Route
import org.konaframework.http.Response
import org.konaframework.session.Csrf
import org.konaframework.session.Session
import org.konaframework.session.StartSession
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ThreadLocalRandom
import java.util.function.Function
import java.util.function.Supplier

/**
 * The Brew engine (`LivewireManager` + `HandleComponents` + `HandleRequests`).
 */
class BrewManager @JvmOverloads constructor(
    val config: BrewConfig = BrewConfig(),
    views: CremaEngine? = null,
    val container: Container? = null,
) {
    val registry = ComponentRegistry(container)
    val synths = Synthesizers()
    val checksum = Checksum(config.key)
    val invoker = Invoker(container)

    /** Store for `@Computed(persist / cache)`. */
    var computedCache: ComputedCache = InMemoryComputedCache()
    val assets = org.konaframework.brew.view.FrontendAssets(this)

    /** Effective update endpoint (changed by [setUpdateRoute]). */
    @Volatile
    var updateUri: String = config.updateUri
        private set

    /** Custom script endpoint set by [setScriptRoute], or null for `{path}/brew(.min).js`. */
    @Volatile
    var scriptUri: String? = null
        private set

    private var crema: CremaEngine? = views
    private val hookFactories = CopyOnWriteArrayList<Supplier<out ComponentHook>>()
    private val stack = ThreadLocal.withInitial { ArrayDeque<Component>() }
    private val request = ThreadLocal<Request?>()

    /** When true, `@Lazy` / `@Defer` mount immediately (`Brew.withoutLazyLoading()`). */
    @Volatile
    var lazyDisabled: Boolean = false

    /** Deploy release token written into every snapshot (`Brew.releaseToken`). */
    @Volatile
    var releaseToken: String? = null

    private val persistentMiddleware = CopyOnWriteArrayList<Middleware>()
    private val updateMiddleware = CopyOnWriteArrayList<Middleware>()
    private var uploadRootPath: Path? = null

    init {
        synths.register(ModelCollectionSynth)
        synths.register(ModelSynth)
        componentHook { SupportSlots() }
        componentHook { SupportNesting() }
        componentHook { SupportLazyLoading() }
        componentHook { SupportLifecycleHooks() }
        componentHook { SupportLockedProperties() }
        componentHook { SupportComputed() }
        componentHook { SupportMagicActions() }
        componentHook { SupportValidation() }
        componentHook { SupportTransitions() }
        componentHook { SupportEvents() }
        componentHook { SupportJsEvaluation() }
        componentHook { SupportRedirects() }
        componentHook { SupportStreaming() }
        componentHook { SupportDownloads() }
        componentHook { SupportScripts() }
        componentHook { SupportEcho() }
        componentHook { SupportIslands() }
        componentHook { SupportPages() }
        componentHook { SupportQueryString() }
        componentHook { SupportSession() }
        componentHook { SupportPagination() }
        componentHook { SupportLocales() }
        componentHook { SupportReleaseTokens() }
        componentHook { SupportAuthorize() }
        componentHook { SupportFileUploads() }
        componentHook { SupportStyles() }
        componentHook { SupportJsModules() }
        views?.let { BrewCrema.install(it, this) }
    }

    /** Disable lazy/defer for the duration of [block] (tests). */
    fun <T> withoutLazyLoading(block: () -> T): T {
        val previous = lazyDisabled
        lazyDisabled = true
        try {
            return block()
        } finally {
            lazyDisabled = previous
        }
    }

    /** Attach the Crema engine used for component views (installs `<brew:…>`, `@brew`, `@brewScripts`, …). */
    fun setViews(engine: CremaEngine): BrewManager {
        crema = engine
        BrewCrema.install(engine, this)
        return this
    }

    fun views(): CremaEngine = crema ?: throw BrewException("No Crema engine configured for Brew; register CremaServiceProvider")

    // ---- Registration -------------------------------------------------------------------------------------------

    /** Register a feature hook factory (`ComponentHookRegistry::register`). */
    fun componentHook(factory: Supplier<out ComponentHook>): BrewManager {
        hookFactories += factory
        return this
    }

    @JvmSynthetic
    fun componentHook(factory: () -> ComponentHook): BrewManager = componentHook(Supplier { factory() })

    fun component(name: String, type: Class<out Component>): BrewManager {
        registry.component(name, type)
        return this
    }

    fun component(type: Class<out Component>): BrewManager {
        registry.component(type)
        return this
    }

    @JvmSynthetic
    fun component(name: String, factory: () -> Component): BrewManager {
        registry.component(name, factory)
        return this
    }

    fun component(name: String, factory: Supplier<out Component>): BrewManager {
        registry.component(name, factory)
        return this
    }

    fun propertySynthesizer(synth: Synth): BrewManager {
        synths.register(synth)
        return this
    }

    // ---- Component lifecycle ------------------------------------------------------------------------------------

    /** Instantiate and attach component [name] (`app('livewire')->new()`). */
    @JvmOverloads
    fun newComponent(name: String, id: String? = null): Component {
        val component = registry.create(name)
        val state = ComponentState(id ?: newId(), name, this)
        component.attach(state)
        val hooks = hookFactories.map { f ->
            f.get().also {
                it.component = component
                it.manager = this
            }
        }.filterNot { it.skip() }
        state.hooks = HookSet(hooks)
        return component
    }

    /** Component currently mounting / updating on this thread (`app('livewire')->current()`). */
    fun current(): Component? = stack.get().lastOrNull()

    /** Mount/update stack for this thread (outermost parent first). */
    fun currentStack(): List<Component> = stack.get().toList()

    /** Request being handled on this thread, if any. */
    fun currentRequest(): Request? = request.get()

    /** Run [block] with [current] request exposed to components (`csrf`, paths, sessions). */
    fun <T> withRequest(current: Request?, block: () -> T): T {
        val previous = request.get()
        request.set(current)
        try {
            return block()
        } finally {
            request.set(previous)
        }
    }

    /** Mount [name] and return its initial HTML with `wire:snapshot` / `wire:effects` (`HandleComponents::mount`). */
    @JvmOverloads
    fun mount(name: String, params: Map<String, Any?> = emptyMap(), key: String? = null): String =
        mountComponent(name, params, key).html!!

    /** Mount and return the full [ComponentResult] (used by testing and nesting). */
    fun mountComponent(name: String, params: Map<String, Any?>, key: String?): ComponentResult {
        currentRequest()?.setAttribute(RENDERED_ATTRIBUTE, true)
        val parent = stack.get().lastOrNull()
        val component = newComponent(name)
        val context = ComponentContext(component, mounting = true)
        stack.get().addLast(component)
        try {
            val state = component.state()
            state.hooks.mount(params, key)
            val html = render(component, context, "<div></div>")!!
            state.hooks.dehydrate(context)
            val snapshot = snapshot(component, context)
            state.hooks.destroy()
            val withSnapshot = HtmlRoot.insertAttributes(
                html,
                linkedMapOf("wire:snapshot" to json(snapshot), "wire:effects" to if (context.effects.isEmpty()) "[]" else json(context.effects)),
                name,
            )
            if (parent != null) {
                SupportNesting.trackChild(parent, component.id(), HtmlRoot.rootTagName(withSnapshot), key)
            }
            return ComponentResult(component, withSnapshot, snapshot, context)
        } finally {
            stack.get().removeLast()
        }
    }

    /**
     * Hydrate from [snapshot], apply [updates], run [calls], render (`HandleComponents::update`).
     * Returns the new snapshot and the effects.
     */
    fun update(
        snapshot: Map<String, Any?>,
        updates: Map<String, Any?>,
        calls: List<Map<String, Any?>>,
    ): Pair<Map<String, Any?>, Map<String, Any?>> = updateComponent(snapshot, updates, calls).let { it.snapshot to it.effects }

    /** Like [update] but returns the full [ComponentResult]. */
    fun updateComponent(
        snapshot: Map<String, Any?>,
        updates: Map<String, Any?>,
        calls: List<Map<String, Any?>>,
    ): ComponentResult {
        val memo = snapshot["memo"] as? Map<*, *>
        if (snapshot["data"] !is Map<*, *> || memo == null || snapshot["checksum"] !is String ||
            memo["id"] !is String || memo["name"] !is String
        ) {
            throw CorruptComponentPayloadException()
        }
        for (call in calls) {
            if (call["method"] !is String || call["params"] !is List<*>) throw CorruptComponentPayloadException()
        }
        checksum.verify(snapshot, debug = config.debug)
        @Suppress("UNCHECKED_CAST")
        val data = snapshot["data"] as Map<String, Any?>
        val component = newComponent(memo["name"] as String, memo["id"] as String)
        val context = ComponentContext(component, mounting = false)
        hydrateProperties(component, data)
        stack.get().addLast(component)
        try {
            val state = component.state()
            @Suppress("UNCHECKED_CAST")
            state.hooks.hydrate(memo as Map<String, Any?>, context)
            updateProperties(component, updates, context)
            callMethods(component, calls, context)
            val html = render(component, context, null)?.also { context.addEffect("html", it) }
            state.hooks.dehydrate(context)
            val next = snapshot(component, context)
            state.hooks.destroy()
            return ComponentResult(component, html, next, context)
        } finally {
            stack.get().removeLast()
        }
    }

    /** Snapshot for [component] (`HandleComponents::snapshot`). */
    fun snapshot(component: Component, context: ComponentContext): Map<String, Any?> {
        val data = LinkedHashMap<String, Any?>()
        for ((name, prop) in ComponentMeta.of(component).properties) {
            data[name] = synths.dehydrate(prop.get(component), synths.context(name))
        }
        val memo = LinkedHashMap<String, Any?>()
        memo["id"] = component.id()
        memo["name"] = component.name()
        val request = currentRequest()
        memo["path"] = if (request == null) "" else request.path.trimStart('/')
        memo["method"] = if (request == null) "GET" else request.method
        @Suppress("UNCHECKED_CAST")
        memo["children"] = (component.state().store["children"] as? Map<String, Any?>) ?: emptyMap<String, Any?>()
        memo["scripts"] = emptyList<Any?>()
        memo["assets"] = emptyList<Any?>()
        memo["errors"] = emptyList<Any?>()
        memo["locale"] = java.util.Locale.getDefault().language
        val async = ComponentMeta.of(component).async
        if (async.isNotEmpty()) memo["async"] = async.toList()
        memo.putAll(context.memo)
        val snapshot = linkedMapOf<String, Any?>("data" to data, "memo" to memo)
        snapshot["checksum"] = checksum.generate(snapshot)
        return snapshot
    }

    private fun hydrateProperties(component: Component, data: Map<String, Any?>) {
        val props = ComponentMeta.of(component).properties
        for ((key, raw) in data) {
            val prop = props[key] ?: continue
            val value = synths.hydrate(raw, synths.context(key), prop.genericType)
            if (value == null && !prop.nullable) continue
            prop.set(component, value)
        }
    }

    private fun updateProperties(component: Component, updates: Map<String, Any?>, context: ComponentContext) {
        val finishers = updates.map { (path, value) -> updateProperty(component, path, value, context) }
        finishers.forEach { it() }
    }

    /** Set [path] (dot-notation) to [value] through hooks and synths (`updateProperty`). Returns the finisher. */
    fun updateProperty(component: Component, path: String, value: Any?, context: ComponentContext): () -> Unit {
        val segments = path.split('.')
        config.maxNestingDepth?.let { max ->
            if (segments.size > max) throw PayloadGuardException(422, "Update path [$path] exceeds max nesting depth of $max")
        }
        val property = segments.first()
        val finish = component.state().hooks.update(property, path, value)
        val prop = ComponentMeta.of(component).properties[property]
            ?: throw PublicPropertyNotFoundException(property, component.name())
        val remove = value == Synthesizers.REMOVE_MARKER
        val next = try {
            if (segments.size == 1) {
                Coercion.toType(value, prop.type, prop.genericType)
            } else {
                synths.setPath(prop.get(component), prop.genericType, segments.drop(1), value, remove)
            }
        } catch (e: IllegalArgumentException) {
            throw PropertyTypeMismatchException(path, prop.type.simpleName)
        }
        if (next == null && !prop.nullable) {
            prop.set(component, Coercion.defaultFor(prop.type) ?: prop.get(component))
        } else {
            prop.set(component, next)
        }
        return finish
    }

    private fun callMethods(component: Component, calls: List<Map<String, Any?>>, context: ComponentContext) {
        config.maxCalls?.let { max ->
            if (calls.size > max) throw PayloadGuardException(422, "Too many calls (${calls.size}); max is $max")
        }
        val meta = ComponentMeta.of(component)
        val returns = mutableListOf<Any?>()
        val renderless = calls.isNotEmpty() && calls.all { call ->
            (call["metadata"] as? Map<*, *>)?.get("renderless") == true || call["method"] in meta.renderless
        }
        for (call in calls) {
            val method = call["method"] as String
            val params = call["params"] as List<*>
            @Suppress("UNCHECKED_CAST")
            val metadata = (call["metadata"] as? Map<String, Any?>) ?: emptyMap()
            val returnEarly = ReturnEarly()
            val finish = component.state().hooks.call(method, params, returnEarly, metadata, context)
            if (returnEarly.called) {
                returns += finish(returnEarly.value)
                continue
            }
            val candidates = meta.actions[method] ?: throw MethodNotFoundException(method, component.name())
            val target = candidates.firstOrNull { it.parameterCount == params.size } ?: candidates.maxBy { it.parameterCount }
            val result = wrapped(component) { invoker.call(component, target, params) }
            returns += finish(result)
        }
        if (renderless) component.skipRender()
        context.addEffect("returns", returns)
    }

    /** Invoke user code; the component's `exception()` hook may swallow failures (`Wrapped`). */
    fun <T> wrapped(component: Component, block: () -> T): T? =
        try {
            block()
        } catch (e: Throwable) {
            if (!component.state().hooks.exception(e)) throw e
            null
        }

    /** Render [component]; returns null when rendering is skipped without replacement HTML. */
    fun render(component: Component, context: ComponentContext, default: String?): String? {
        val state = component.state()
        if (state.skipRender) {
            val html = state.skipRenderHtml ?: default ?: return null
            return HtmlRoot.insertAttributes(html, rootAttributes(component), component.name())
        }
        val view = viewFor(component)
        val data = viewData(component, view)
        val finish = state.hooks.render(view, data)
        state.renderedView = view
        state.renderedData = data
        val rendered = wrapped(component) { renderView(view, data) } ?: default ?: return null
        if (config.debug) HtmlRoot.assertSingleRoot(rendered, component.name())
        val html = HtmlRoot.insertAttributes(rendered, rootAttributes(component), component.name())
        return finish(html)
    }

    private fun rootAttributes(component: Component): Map<String, Any?> =
        linkedMapOf("wire:id" to component.id(), "wire:name" to component.name())

    private fun viewFor(component: Component): BrewView {
        return when (val result = wrapped(component) { component.render() }) {
            is BrewView -> result
            is String -> BrewView.raw(result)
            null -> {
                val template = component.template()
                if (template != null) {
                    BrewView.inline(template)
                } else {
                    BrewView.named(config.viewPrefix + "." + component.name().substringAfter("::"))
                }
            }
            else -> throw BrewException("render() of [${component.name()}] must return a BrewView, String, or null")
        }
    }

    private fun viewData(component: Component, view: BrewView): MutableMap<String, Any?> {
        val data = LinkedHashMap<String, Any?>()
        data.putAll(component.all())
        data.putAll(view.data)
        data["this"] = component
        data["_brew"] = component
        csrfToken()?.let { data["_token"] = it }
        return data
    }

    private fun renderView(view: BrewView, data: Map<String, Any?>): String {
        view.html?.let { return it }
        val context = CremaContext().merge(data)
        if (config.injectMorphMarkers) context.set(CremaContext.MORPH_MARKERS, true)
        val engine = views()
        return if (view.name != null) engine.render(view.name, context) else engine.renderSource(view.source!!, context)
    }

    // ---- Computed -----------------------------------------------------------------------------------------------

    fun computedValue(component: Component, name: String): Any? = SupportComputed.value(this, component, name)

    fun forgetComputed(component: Component, name: String) = SupportComputed.forget(this, component, name)

    // ---- HTTP ---------------------------------------------------------------------------------------------------

    /**
     * Register the update endpoint yourself (`Livewire::setUpdateRoute`), e.g. to add middleware or a prefix.
     * The default `{path}/update` route stops answering once a custom route is set.
     */
    fun setUpdateRoute(registrar: Function<Handler, Route>): Route {
        val route = registrar.apply(Handler { handleUpdate(it) })
        updateUri = "/" + route.uri.trimStart('/')
        return route
    }

    /** Serve the runtime from a custom route (`Livewire::setScriptRoute`). */
    fun setScriptRoute(registrar: Function<Handler, Route>): Route {
        val route = registrar.apply(Handler { assets.response(assets.scriptName()) })
        scriptUri = "/" + route.uri.trimStart('/')
        return route
    }

    /** `POST /brew/upload` — accept a signed temporary upload body. */
    fun handleUpload(request: Request): Response {
        val expires = request.query["expires"]?.toLongOrNull() ?: return Response.text("Invalid", 403)
        val signature = request.query["signature"] ?: return Response.text("Invalid", 403)
        val name = request.query["name"] ?: "file"
        val multiple = request.query["multiple"] == "true"
        val fileName = request.query["filename"]
        val size = request.query["size"]
        if (!verifyUploadSignature(name, expires, multiple, fileName, size, signature)) {
            return Response.text("Invalid signature", 403)
        }
        val bytes = request.bodyBytes
        if (bytes.isEmpty()) return Response.text("Empty upload", 422)
        var maxRule: Int? = null
        for (rule in config.uploadRules) {
            if (rule.startsWith("max:")) {
                maxRule = rule.removePrefix("max:").toIntOrNull()
                break
            }
        }
        if (maxRule != null && bytes.size > maxRule * 1024) {
            return Response.text("File too large", 422)
        }
        val path = storeUpload(bytes, fileName ?: "upload.bin")
        return Response.json(linkedMapOf("paths" to listOf(path)))
    }

    /** `GET /brew/preview/{path}` — serve a temporary upload for previews. */
    fun handlePreview(request: Request): Response {
        val relative = request.route("path") ?: request.path.substringAfter("/preview/").trimStart('/')
        if (relative.contains("..")) return Response.text("Not Found", 404)
        val file = uploadRoot().resolve(relative)
        if (!Files.exists(file)) return Response.text("Not Found", 404)
        val bytes = Files.readAllBytes(file)
        return Response(200, "", linkedMapOf("Content-Type" to "application/octet-stream"))
            .withBodyBytes(bytes)
    }

    /** `POST /brew/update` (`HandleRequests::handleUpdate`). */
    fun handleUpdate(request: Request): Response {
        if (request.header("X-Brew") == null) return Response.text("Missing X-Brew header", 403)
        config.maxPayloadSize?.let { max ->
            if (request.bodyBytes.size > max) throw PayloadGuardException(413, "Brew payload exceeds $max bytes")
        }
        @Suppress("UNCHECKED_CAST")
        val body = try {
            Coercion.mapper.readValue(request.bodyBytes, Map::class.java) as Map<String, Any?>
        } catch (_: Exception) {
            return Response.text("Malformed Brew payload", 404)
        }
        verifyCsrf(request, body["_token"] as? String)
        val components = body["components"] as? List<*>
        if (components.isNullOrEmpty() || components.any { !validComponentPayload(it) }) {
            return Response.text("Malformed Brew payload", 404)
        }
        config.maxComponents?.let { max ->
            if (components.size > max) throw PayloadGuardException(422, "Too many components (${components.size}); max is $max")
        }
        request.setAttribute(UPDATE_ATTRIBUTE, true)
        val results = withRequest(request) {
            components.map { raw ->
                val payload = raw as Map<*, *>
                @Suppress("UNCHECKED_CAST")
                val snapshot = parseSnapshot(payload["snapshot"] as String)
                @Suppress("UNCHECKED_CAST")
                val result = updateComponent(
                    snapshot,
                    payload["updates"] as Map<String, Any?>,
                    payload["calls"] as List<Map<String, Any?>>,
                )
                linkedMapOf("snapshot" to json(result.snapshot), "effects" to result.effects)
            }
        }
        return Response.json(linkedMapOf("components" to results, "assets" to emptyList<Any?>()))
    }

    private fun validComponentPayload(raw: Any?): Boolean {
        val payload = raw as? Map<*, *> ?: return false
        return payload["snapshot"] is String && payload["updates"] is Map<*, *> && payload["calls"] is List<*>
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseSnapshot(text: String): Map<String, Any?> = try {
        Coercion.mapper.readValue(text, Map::class.java) as Map<String, Any?>
    } catch (_: Exception) {
        throw CorruptComponentPayloadException()
    }

    private fun verifyCsrf(request: Request, token: String?) {
        if (!config.csrf) return
        val session = request.getAttribute(StartSession.ATTRIBUTE) as? Session ?: return
        val provided = token ?: request.header("X-CSRF-TOKEN") ?: request.header("X-XSRF-TOKEN")
        if (!Csrf.tokensMatch(session.get(Csrf.TOKEN_KEY) as? String, provided)) {
            throw org.konaframework.session.TokenMismatchException()
        }
    }

    /** CSRF token for the current request's session (generated when missing). */
    fun csrfToken(): String? {
        val session = currentRequest()?.getAttribute(StartSession.ATTRIBUTE) as? Session ?: return null
        return Csrf.token(session)
    }

    /** Fresh, unattached instance for reset() defaults. */
    internal fun freshInstance(component: Component): Component = registry.create(component.name())

    internal fun json(value: Any?): String = Coercion.mapper.writeValueAsString(value)

    /** Set a deploy release token (`Livewire::releaseToken`). */
    fun releaseToken(token: String?): BrewManager {
        releaseToken = token
        return this
    }

    /** Persistent middleware re-applied on every update (`Livewire::addPersistentMiddleware`). */
    fun addPersistentMiddleware(vararg middleware: Middleware): BrewManager {
        persistentMiddleware += middleware
        return this
    }

    /** Global middleware on the update route (`Livewire::updateMiddleware`). */
    fun updateMiddleware(vararg middleware: Middleware): BrewManager {
        updateMiddleware += middleware
        return this
    }

    fun persistentMiddleware(): List<Middleware> = persistentMiddleware.toList()

    fun updateMiddleware(): List<Middleware> = updateMiddleware.toList()

    /** Root directory for temporary uploads. */
    fun uploadRoot(): Path {
        val existing = uploadRootPath
        if (existing != null) return existing
        val created = Files.createTempDirectory("brew-uploads")
        uploadRootPath = created
        return created
    }

    fun uploadRoot(path: Path): BrewManager {
        Files.createDirectories(path)
        uploadRootPath = path
        return this
    }

    /** Sign a temporary upload URL (`_startUpload`). */
    fun signUpload(name: String, fileInfo: Map<String, Any?>, multiple: Boolean): SignedUpload {
        val expires = System.currentTimeMillis() / 1000 + (config.uploadTimeoutSeconds)
        val payload = "$name|$expires|$multiple|${fileInfo["name"]}|${fileInfo["size"]}"
        val token = hmac(payload)
        val url = "${config.path}/upload?expires=$expires&signature=$token"
        return SignedUpload(url, token)
    }

    /**
     * Pre-signed S3-compatible PUT details when `brew.temporary_file_upload.disk=s3`
     * (`WithFileUploads` / Livewire S3 driver). Falls back to local signing when no endpoint is set.
     */
    fun signS3Upload(name: String, fileInfo: Map<String, Any?>, multiple: Boolean): Map<String, Any?> {
        val endpoint = config.uploadS3Endpoint?.trimEnd('/')
        if (endpoint.isNullOrBlank()) {
            val local = signUpload(name, fileInfo, multiple)
            return linkedMapOf("uploadUrl" to local.url, "token" to local.token, "isS3" to false)
        }
        val expires = System.currentTimeMillis() / 1000 + config.uploadTimeoutSeconds
        val key = "livewire-tmp/" + java.util.UUID.randomUUID() + "-" + (fileInfo["name"] ?: "upload.bin")
        val payload = "s3|$name|$expires|$multiple|$key|${fileInfo["size"]}"
        val signature = hmac(payload)
        val url = "$endpoint/$key?Expires=$expires&Signature=$signature"
        return linkedMapOf(
            "isS3" to true,
            "uploadUrl" to url,
            "token" to signature,
            "path" to key,
            "headers" to linkedMapOf("Content-Type" to (fileInfo["type"] ?: "application/octet-stream")),
        )
    }

    /** Persist an uploaded body under the temp disk and return the relative path. */
    fun storeUpload(bytes: ByteArray, originalName: String): String {
        val root = uploadRoot()
        val relative = "livewire-tmp/" + java.util.UUID.randomUUID() + "-" + originalName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val dest = root.resolve(relative)
        Files.createDirectories(dest.parent)
        Files.write(dest, bytes)
        return relative
    }

    /** Verify a signed upload token is still valid. */
    fun verifyUploadSignature(name: String, expires: Long, multiple: Boolean, fileName: String?, size: Any?, signature: String): Boolean {
        if (expires < System.currentTimeMillis() / 1000) return false
        val payload = "$name|$expires|$multiple|$fileName|$size"
        return hmac(payload) == signature
    }

    /** Delete temporary uploads older than [config.uploadCleanupSeconds]. */
    fun cleanupOldUploads(): Int {
        val root = uploadRootPath ?: return 0
        val cutoff = System.currentTimeMillis() - config.uploadCleanupSeconds * 1000L
        var removed = 0
        Files.walk(root).use { stream ->
            stream.filter { Files.isRegularFile(it) }.forEach { path ->
                if (Files.getLastModifiedTime(path).toMillis() < cutoff) {
                    Files.deleteIfExists(path)
                    removed++
                }
            }
        }
        return removed
    }

    private fun hmac(payload: String): String {
        val key = config.key.ifBlank { "brew-upload" }.toByteArray()
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return HexFormat.of().formatHex(mac.doFinal(payload.toByteArray()))
    }

    /** Run update middlewares then [handleUpdate] (used by custom route wiring). */
    fun handleUpdateWithMiddleware(request: Request): Response {
        var handler = Handler { handleUpdate(it) }
        for (mw in (updateMiddleware + persistentMiddleware).asReversed()) {
            val next = handler
            handler = Handler { req -> mw.handle(req, next) }
        }
        return handler.handle(request)
    }

    private fun newId(): String {
        val random = ThreadLocalRandom.current()
        val chars = CharArray(20) { ALPHABET[random.nextInt(ALPHABET.length)] }
        return String(chars)
    }

    companion object {
        /** Request attribute set while handling `POST /brew/update`. */
        const val UPDATE_ATTRIBUTE = "brew.update"

        /** Request attribute set once any component rendered (drives asset injection). */
        const val RENDERED_ATTRIBUTE = "brew.rendered"

        private const val ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    }
}
