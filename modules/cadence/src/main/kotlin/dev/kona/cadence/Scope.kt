package dev.kona.cadence

/**
 * A constraint applied to every [ModelBuilder] for a model class unless removed with
 * [ModelBuilder.withoutGlobalScope] / [ModelBuilder.withoutGlobalScopes].
 *
 * Implementations should only add query constraints (`where`, `join`, …). Do not call
 * [ModelBuilder.get] or [ModelBuilder.toBase] from [apply].
 */
fun interface Scope {
    fun apply(builder: ModelBuilder<*>)
}

/** Per-model registry of named [Scope]s (Eloquent-style global scopes). */
internal object GlobalScopes {
    private val scopes = java.util.concurrent.ConcurrentHashMap<Class<*>, MutableMap<String, Scope>>()

    fun add(modelClass: Class<*>, name: String, scope: Scope) {
        scopes.computeIfAbsent(modelClass) { linkedMapOf() }[name] = scope
    }

    fun add(modelClass: Class<*>, scope: Scope) {
        add(modelClass, scope.javaClass.name, scope)
    }

    fun remove(modelClass: Class<*>, name: String) {
        scopes[modelClass]?.remove(name)
    }

    fun clear(modelClass: Class<*>) {
        scopes.remove(modelClass)
    }

    fun all(modelClass: Class<*>): Map<String, Scope> =
        scopes[modelClass]?.let { LinkedHashMap(it) } ?: emptyMap()

    fun has(modelClass: Class<*>, name: String): Boolean = scopes[modelClass]?.containsKey(name) == true

    fun get(modelClass: Class<*>, name: String): Scope? = scopes[modelClass]?.get(name)
}
