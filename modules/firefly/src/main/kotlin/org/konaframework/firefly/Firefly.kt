package org.konaframework.firefly

import org.konaframework.auth.authorization.Gate
import org.konaframework.auth.authorization.Policy
import org.konaframework.core.Application
import org.konaframework.firefly.actions.Action
import org.konaframework.firefly.actions.ActionGate
import org.konaframework.filesystem.FilesystemManager
import org.konaframework.firefly.forms.FireflyUploads
import org.konaframework.firefly.forms.UploadDisks
import org.konaframework.http.Request
import org.konaframework.http.Router
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Function
import java.util.function.Supplier

/**
 * Registers panels and mounts them when the application boots.
 *
 * Panels under `app/panels` are discovered. Call [register] for a panel you build by hand.
 *
 * Soft reload (`grind serve` watch) builds a new [Application] while this object stays in the
 * parent class loader. [prepare] / [mount] clear the prior mount so panels bind to the new router.
 */
object Firefly {
    private val manual = ArrayList<Supplier<Panel>>()
    private val mounted = AtomicBoolean(false)

    @Volatile
    private var mountedApp: Application? = null

    @Volatile
    private var defaultProfile: Supplier<ProfilePage>? = null

    @Volatile
    private var gate: Gate? = null

    @Volatile
    private var allowWithoutPolicy: Boolean = true

    @Volatile
    private var policyPackages: List<String> = listOf(DEFAULT_POLICY_PACKAGE)

    /** Models whose policy package lookup already ran, so a missing policy is looked up once. */
    private val lookedUp: MutableSet<Class<*>> = ConcurrentHashMap.newKeySet()

    /**
     * Global default [ProfilePage] for panels that do not call [Panel.profilePage].
     * Cleared by [reset].
     */
    @JvmStatic
    fun defaultProfilePage(page: ProfilePage): Firefly = apply {
        defaultProfile = Supplier { page }
    }

    /** Builds a profile page when a panel needs the global default. */
    @JvmStatic
    fun defaultProfilePage(provider: Supplier<ProfilePage>): Firefly = apply {
        defaultProfile = provider
    }

    /**
     * Resolve `Action.authorize("ability")` through the Kona [Gate]: the ability is asked as
     * `gate.allows(ability, record)` for the record the action is mounted on (a policy method on the
     * record's model policy, or an ability defined on the gate), so `authorize("post")` hides the
     * action for users the policy denies. Pass null to clear. [reset] leaves it installed.
     */
    @JvmStatic
    fun authorizeActionsWith(gate: Gate?): Firefly = apply {
        Action.gate(if (gate == null) null else ActionGate { ability, _, schema -> gate.allows(ability, schema.record()) })
    }

    internal fun resolveDefaultProfilePage(): ProfilePage? = defaultProfile?.get()

    /** The tenant model of the current panel request, or null. Same as [FireflyTenant.current]. */
    @JvmStatic
    fun getTenant(): Any? = FireflyTenant.current()

    /** [getTenant] as [type]. */
    @JvmStatic
    fun <T> getTenant(type: Class<T>): T? = FireflyTenant.current(type)


    /** Keeps [panel] and mounts it on the next [mount]. The same instance is used. */
    @JvmStatic
    fun register(panel: Panel): Firefly = apply {
        manual.add(Supplier { panel })
    }

    /** Builds a panel when the application mounts. Invoked once. */
    @JvmStatic
    fun register(provider: Supplier<Panel>): Firefly = apply {
        manual.add(provider)
    }

    /** Drops registrations. The next [mount] runs again. */
    @JvmStatic
    fun reset() {
        manual.clear()
        defaultProfile = null
        gate = null
        allowWithoutPolicy = true
        policyPackages = listOf(DEFAULT_POLICY_PACKAGE)
        lookedUp.clear()
        mounted.set(false)
        mountedApp = null
    }

    /**
     * Clears a stale mount when [app] is a new [Application] (grind soft reload).
     * Call from [FireflyServiceProvider.register] before hand [register] calls so new
     * manuals are not wiped by a later remount.
     */
    @JvmStatic
    fun prepare(app: Application) {
        val previous = mountedApp
        if (previous != null && previous !== app) {
            reset()
        }
    }

    /**
     * Mounts hand-registered panels, then every panel class under `app/panels`
     * and [FireflyConfig.discovery]. A second call for the same [app] does nothing.
     * When the router is not bound yet, this returns and a later call can mount.
     */
    @JvmStatic
    fun mount(app: Application) {
        prepare(app)
        wireAuthorization(app)
        if (!mounted.compareAndSet(false, true)) return
        if (!app.container.bound(Router::class.java)) {
            mounted.set(false)
            return
        }
        val router = app.resolve(Router::class.java)
        FireflyAssets.register(router)
        val hands = ArrayList<Panel>()
        val seen = HashSet<String>()
        for (supplier in manual) {
            val panel = supplier.get()
            hands.add(panel)
            seen.add(panel.javaClass.name)
        }
        for (panel in hands) {
            panel.mount(router)
        }
        for (panel in discover(app)) {
            if (seen.add(panel.javaClass.name)) {
                hands.add(panel)
                panel.mount(router)
            }
        }
        if (app.container.bound(FilesystemManager::class.java)) {
            val files = app.resolve(FilesystemManager::class.java)
            FireflyUploads.register(router, UploadDisks { files.disk(it) }, Function { request -> uploadAllowed(request, hands) })
        }
        mountedApp = app
    }

    /**
     * The [Gate] resources, relation managers and panels ask. Without one, Firefly uses the
     * container's gate when the application boots, else [Gate.getDefault] when one is set.
     * Pass null to go back to that default.
     */
    @JvmStatic
    fun gate(gate: Gate?): Firefly = apply {
        this.gate = gate
        lookedUp.clear()
    }

    /**
     * What a resource answers when its model has no policy (or no gate exists). The default is
     * true: everything is allowed (Filament default). Pass false for strict mode, where a model
     * without a policy is denied. [Resource.authorizeWithoutPolicy] and [Panel.authorizeWithoutPolicy]
     * override this per resource or panel.
     */
    @JvmStatic
    fun authorizeWithoutPolicy(on: Boolean): Firefly = apply { allowWithoutPolicy = on }

    @JvmStatic
    fun authorizesWithoutPolicy(): Boolean = allowWithoutPolicy

    /**
     * Packages searched for `{Model}Policy` when a resource model has no registered policy
     * (default `app.policies`). The policy needs a no-argument constructor and must implement [Policy].
     */
    @JvmStatic
    fun policyPackages(vararg packages: String): Firefly = apply {
        policyPackages = packages.filter { it.isNotBlank() }
        lookedUp.clear()
    }

    internal fun activeGate(): Gate? {
        val explicit = gate
        if (explicit != null) return explicit
        if (Gate.hasDefault()) return Gate.getDefault()
        return null
    }

    /**
     * The policy for [model]: one registered on the gate, else one discovered by convention
     * (`app.policies.{Model}Policy`) and registered through [Gate.discoverPolicy].
     */
    internal fun policyFor(model: Class<*>): Policy? {
        val active = activeGate() ?: return null
        val registered = active.getPolicyFor(model)
        if (registered != null) return registered
        if (!lookedUp.add(model)) return null
        for (pack in policyPackages) {
            if (active.discoverPolicy(model, pack)) return active.getPolicyFor(model)
        }
        return null
    }

    /** Takes the container [Gate] and the `firefly.policies` / `firefly.authorize_without_policy` config. */
    private fun wireAuthorization(app: Application) {
        if (gate == null && app.container.bound(Gate::class.java)) {
            gate(app.resolve(Gate::class.java))
        }
        val packages = FireflyConfig.policies(app.config)
        if (packages.isNotEmpty()) policyPackages(*packages.toTypedArray())
        val strict = FireflyConfig.authorizeWithoutPolicy(app.config)
        if (strict != null) authorizeWithoutPolicy(strict)
    }

    internal fun uploadAllowed(request: Request, panels: List<Panel>): Boolean {
        if (FireflyCsrf.rejectIfInvalid(request) != null) return false
        return panels.any { it.allowsUpload(request) }
    }

    private fun discover(app: Application): List<Panel> {
        val config = FireflyConfig.discovery(app.config)
        val found = ArrayList<Panel>()
        val seen = HashSet<String>()
        for (root in roots(app, config)) {
            for (panel in PanelDiscovery.panels(root)) {
                if (seen.add(panel.javaClass.name)) found.add(panel)
            }
        }
        return found
    }

    private fun roots(app: Application, config: List<String>): List<Path> {
        val base = app.basePath()
        val roots = ArrayList<Path>()
        roots.add(base.resolve("src/main/kotlin/app/panels"))
        roots.add(base.resolve("src/main/java/app/panels"))
        for (extra in config) {
            val path = Path.of(extra)
            roots.add(if (path.isAbsolute) path else base.resolve(path))
        }
        return roots
    }

    private const val DEFAULT_POLICY_PACKAGE = "app.policies"
}
