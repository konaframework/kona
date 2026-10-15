package dev.kona.extract

import dev.kona.extract.relations.BelongsTo
import dev.kona.extract.relations.BelongsToMany
import dev.kona.extract.relations.EagerLoader
import dev.kona.extract.relations.HasMany
import dev.kona.extract.relations.HasManyThrough
import dev.kona.extract.relations.HasOne
import dev.kona.extract.relations.HasOneThrough
import dev.kona.extract.relations.MorphMany
import dev.kona.extract.relations.MorphMap
import dev.kona.extract.relations.MorphOne
import dev.kona.extract.relations.MorphTo
import dev.kona.extract.relations.Relation
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * ActiveRecord model base. Subclasses need a no-arg constructor and pass themselves as [T].
 *
 * ```kotlin
 * class User : Extract<User>() {
 *     override val fillable = setOf("name", "email")
 *     override val casts = mapOf("admin" to CastType.BOOLEAN, "money" to MoneyCast())
 *     companion object : ExtractCompanion<User>(User::class.java)
 * }
 * val ada = User.create(mapOf("name" to "Ada"))
 * ```
 * ```java
 * public class User extends Extract<User> {
 *     public static final ExtractCompanion<User> QUERY = Extract.of(User.class);
 *     @Override public Set<String> getFillable() { return Set.of("name", "email"); }
 * }
 * ```
 *
 * Attributes hold the stored (database) values; [getAttribute] applies [effectiveCasts] on read.
 * Override the `open` properties to change table, key, timestamps, mass assignment, casts, or connection.
 */
abstract class Extract<T : Extract<T>> {
    /** Table name; defaults to the snake-case plural of the class name (`BlogPost` → `blog_posts`). */
    open val table: String
        get() = tableNameFor(javaClass.simpleName)

    open val primaryKey: String
        get() = "id"

    /** When true, inserts read the generated key back into [primaryKey]. */
    open val incrementing: Boolean
        get() = true

    /** When true, `created_at` / `updated_at` are maintained and cast to `Instant`. */
    open val timestamps: Boolean
        get() = true

    open val createdAtColumn: String
        get() = "created_at"

    open val updatedAtColumn: String
        get() = "updated_at"

    /** Mass-assignable keys for [fill]; when non-empty, other keys are silently skipped. */
    open val fillable: Set<String>
        get() = emptySet()

    /** Keys [fill] refuses; `*` (the default) guards everything not in [fillable]. */
    open val guarded: Set<String>
        get() = setOf("*")

    /**
     * Column → cast declarations. Values are [CastType] enums or [CastsAttributes] instances.
     * Java overrides return `Map<String, Object>`.
     */
    open val casts: Map<String, Any>
        get() = emptyMap()

    /** Named connection on the global [Database]; null uses its default connection. */
    open val connectionName: String?
        get() = null

    open val perPage: Int
        get() = 15

    /** Attribute keys omitted from [toArray] / [toJson] unless listed in [visible]. */
    open val hidden: Set<String>
        get() = emptySet()

    /** When non-empty, only these keys (plus [appends]) appear in serialization. */
    open val visible: Set<String>
        get() = emptySet()

    /** Virtual attribute names resolved via `get{Name}Attribute` / [getAppendedAttribute]. */
    open val appends: Set<String>
        get() = emptySet()

    private val attributes = linkedMapOf<String, Any?>()
    private var original: Map<String, Any?> = emptyMap()
    private var changes: Map<String, Any?> = emptyMap()
    private val relations = linkedMapOf<String, Any?>()
    private var persisted = false
    private var recentlyCreated = false
    private var hiddenOverrides: MutableSet<String>? = null
    private var visibleOverrides: MutableSet<String>? = null
    private var appendOverrides: MutableSet<String>? = null

    @Suppress("UNCHECKED_CAST")
    private fun self(): T = this as T

    /** True once the model was loaded from or saved to the database (and not hard-deleted). */
    fun exists(): Boolean = persisted

    /** True when the last [save] inserted this model. */
    fun wasRecentlyCreated(): Boolean = recentlyCreated

    // ---- attributes ----

    operator fun get(key: String): Any? = getAttribute(key)

    operator fun set(key: String, value: Any?) {
        setAttribute(key, value)
    }

    fun getAttribute(key: String): Any? {
        val raw = attributes[key]
        val value = applyCastGet(key, raw)
        return getMutatedAttribute(key, value)
    }

    /** Typed read: `val name: String? = user.attribute("name")`. */
    @Suppress("UNCHECKED_CAST")
    fun <V> attribute(key: String): V? = getAttribute(key) as V?

    fun setAttribute(key: String, value: Any?): T {
        val mutated = setMutatedAttribute(key, value)
        attributes[key] = applyCastSet(key, mutated)
        return self()
    }

    /** Hook after cast on read; override for accessors. */
    protected open fun getMutatedAttribute(key: String, value: Any?): Any? = value

    /** Hook before cast on write; override for mutators. */
    protected open fun setMutatedAttribute(key: String, value: Any?): Any? = value

    fun hasAttribute(key: String): Boolean = attributes.containsKey(key)

    fun getRawAttribute(key: String): Any? = attributes[key]

    fun getAttributes(): Map<String, Any?> = LinkedHashMap(attributes)

    fun getOriginal(): Map<String, Any?> = LinkedHashMap(original)

    fun getOriginal(key: String): Any? = original[key]

    /** Attributes changed since load / last save. */
    fun getDirty(): Map<String, Any?> =
        attributes.filter { (key, value) -> !original.containsKey(key) || original[key] != value }

    fun isDirty(): Boolean = getDirty().isNotEmpty()

    fun isDirty(key: String): Boolean = getDirty().containsKey(key)

    fun isClean(): Boolean = !isDirty()

    fun isClean(key: String): Boolean = !isDirty(key)

    fun syncOriginal(): T {
        original = LinkedHashMap(attributes)
        return self()
    }

    /**
     * Attributes that changed during the last successful [save] (empty before the first save,
     * and after a no-op update when the model was already clean).
     */
    fun getChanges(): Map<String, Any?> = LinkedHashMap(changes)

    /** True when the last [save] persisted at least one attribute change. */
    fun wasChanged(): Boolean = changes.isNotEmpty()

    /** True when [key] was among the attributes persisted by the last [save]. */
    fun wasChanged(key: String): Boolean = changes.containsKey(key)

    /**
     * Declared [casts] plus `Instant` casts for timestamp and soft-delete columns.
     * Values are [CastType] or [CastsAttributes].
     */
    fun effectiveCasts(): Map<String, Any> {
        val result = linkedMapOf<String, Any>()
        if (timestamps) {
            result[createdAtColumn] = CastType.INSTANT
            result[updatedAtColumn] = CastType.INSTANT
        }
        if (this is SoftDeletes) {
            result[deletedAtColumn] = CastType.INSTANT
        }
        result.putAll(casts)
        return result
    }

    private fun applyCastGet(key: String, raw: Any?): Any? =
        when (val cast = effectiveCasts()[key]) {
            null -> raw
            is CastType -> cast.get(raw)
            is CastsAttributes -> cast.get(this, key, raw, attributes)
            else -> throw ExtractException("Invalid cast for [$key]: ${cast.javaClass.name}")
        }

    private fun applyCastSet(key: String, value: Any?): Any? =
        when (val cast = effectiveCasts()[key]) {
            null -> value
            is CastType -> cast.set(value)
            is CastsAttributes -> cast.set(this, key, value, attributes)
            else -> throw ExtractException("Invalid cast for [$key]: ${cast.javaClass.name}")
        }

    // ---- mass assignment ----

    /** Sets fillable keys; throws [MassAssignmentException] for other keys on a totally guarded model. */
    fun fill(attributes: Map<String, Any?>): T {
        val totallyGuarded = isTotallyGuarded()
        attributes.forEach { (key, value) ->
            if (isFillable(key)) {
                setAttribute(key, value)
            } else if (totallyGuarded) {
                throw MassAssignmentException(key, javaClass)
            }
        }
        return self()
    }

    /** Sets every key, ignoring [fillable] / [guarded]. */
    fun forceFill(attributes: Map<String, Any?>): T {
        attributes.forEach { (key, value) -> setAttribute(key, value) }
        return self()
    }

    fun isFillable(key: String): Boolean = key in fillable || (fillable.isEmpty() && !isGuarded(key))

    fun isGuarded(key: String): Boolean = "*" in guarded || key in guarded

    fun isTotallyGuarded(): Boolean = fillable.isEmpty() && "*" in guarded

    // ---- persistence ----

    /** Inserts or updates (dirty attributes only); maintains timestamps. */
    fun save(): Boolean {
        val creating = !persisted
        ModelDispatcher.dispatch(ModelEvent.SAVING, this)
        if (creating) {
            ModelDispatcher.dispatch(ModelEvent.CREATING, this)
        } else {
            if (isClean()) {
                changes = emptyMap()
                ModelDispatcher.dispatch(ModelEvent.SAVED, this)
                return true
            }
            ModelDispatcher.dispatch(ModelEvent.UPDATING, this)
        }
        if (persisted) {
            if (timestamps) {
                touchTimestamps()
            }
            val dirty = getDirty()
            keyQuery().update(dirty)
            changes = LinkedHashMap(dirty)
            ModelDispatcher.dispatch(ModelEvent.UPDATED, this)
        } else {
            if (timestamps) {
                touchTimestamps()
            }
            val query = connection().table(table)
            if (incrementing) {
                val id = query.insertGetId(attributes)
                if (id != null) {
                    attributes[primaryKey] = id
                }
            } else {
                query.insert(attributes)
            }
            persisted = true
            recentlyCreated = true
            changes = LinkedHashMap(attributes)
            ModelDispatcher.dispatch(ModelEvent.CREATED, this)
        }
        syncOriginal()
        ModelDispatcher.dispatch(ModelEvent.SAVED, this)
        return true
    }

    fun update(attributes: Map<String, Any?>): Boolean = fill(attributes).save()

    /** [save] with model events muted. */
    fun saveQuietly(): Boolean = ModelDispatcher.withoutEvents { save() }

    /** Deletes the row (or trashes it for [SoftDeletes] models); false when not persisted. */
    fun delete(): Boolean {
        if (!persisted) {
            return false
        }
        ModelDispatcher.dispatch(ModelEvent.DELETING, this)
        if (this is SoftDeletes) {
            runSoftDelete(deletedAtColumn)
            ModelDispatcher.dispatch(ModelEvent.TRASHED, this)
        } else {
            keyQuery().delete()
            persisted = false
        }
        ModelDispatcher.dispatch(ModelEvent.DELETED, this)
        return true
    }

    /** [delete] with model events muted. */
    fun deleteQuietly(): Boolean = ModelDispatcher.withoutEvents { delete() }

    /**
     * Unsaved clone of this model's attributes, excluding the primary key, timestamps,
     * soft-delete column, and any [except] keys.
     */
    @JvmOverloads
    fun replicate(except: Collection<String> = emptyList()): T {
        val skip = linkedSetOf(primaryKey)
        skip.addAll(except)
        if (timestamps) {
            skip += createdAtColumn
            skip += updatedAtColumn
        }
        if (this is SoftDeletes) {
            skip += deletedAtColumn
        }
        val copy = newInstance()
        attributes.forEach { (key, value) ->
            if (key !in skip) {
                copy.attributes[key] = value
            }
        }
        return copy
    }

    /** True when [other] is the same class and shares this model's non-null primary key. */
    fun `is`(other: Extract<*>?): Boolean {
        if (other == null || other.javaClass != javaClass) {
            return false
        }
        val key = getKey() ?: return false
        return Relation.normalizeKey(key) == Relation.normalizeKey(other.getKey())
    }

    fun isNot(other: Extract<*>?): Boolean = !`is`(other)

    /** Bumps `updated_at` (when timestamps are enabled) and persists it; false when not persisted. */
    fun touch(): Boolean {
        if (!persisted || !timestamps) {
            return false
        }
        val now = freshTimestamp()
        attributes[updatedAtColumn] = now
        keyQuery().update(mapOf(updatedAtColumn to now))
        original = original + (updatedAtColumn to now)
        return true
    }


    /**
     * Atomically increments [column] by [amount] in the database and on this instance.
     * Returns rows affected (0 when not persisted).
     */
    @JvmOverloads
    fun increment(column: String, amount: Number = 1): Int {
        if (!persisted) {
            return 0
        }
        val affected = keyQuery().increment(column, amount)
        val current = attributes[column]
        val next = when (current) {
            is Number -> current.toDouble() + amount.toDouble()
            else -> amount.toDouble()
        }
        attributes[column] = coerceIncrementResult(current, next)
        if (timestamps) {
            val now = freshTimestamp()
            attributes[updatedAtColumn] = now
            keyQuery().update(mapOf(updatedAtColumn to now))
            original = original + (column to attributes[column]) + (updatedAtColumn to now)
        } else {
            original = original + (column to attributes[column])
        }
        return affected
    }

    /** Atomically decrements [column] by [amount] (see [increment]). */
    @JvmOverloads
    fun decrement(column: String, amount: Number = 1): Int = increment(column, -amount.toDouble())

    private fun coerceIncrementResult(previous: Any?, next: Double): Any =
        when (previous) {
            is Int -> next.toInt()
            is Long -> next.toLong()
            is Float -> next.toFloat()
            is Double -> next
            else -> next
        }

    /** Re-reads this row (including trashed rows) as a new instance; null when not persisted or gone. */
    fun fresh(): T? {
        if (!persisted) {
            return null
        }
        return newQuery().withTrashed().find(getKey())
    }

    /** Reloads this instance's attributes from the database. */
    fun refresh(): T {
        if (!persisted) {
            return self()
        }
        val fresh = fresh() ?: throw ModelNotFoundException(javaClass, listOf(getKey()))
        attributes.clear()
        attributes.putAll(fresh.attributes)
        syncOriginal()
        return self()
    }

    fun getKey(): Any? = attributes[primaryKey]

    /** Current time used for timestamps (microsecond precision). */
    fun freshTimestamp(): Instant = Instant.now().truncatedTo(ChronoUnit.MICROS)

    fun connection(): DatabaseConnection {
        val database = Database.global()
        val name = connectionName
        return if (name == null) database.connection() else database.connection(name)
    }

    // ---- query / instance factories ----

    /** Query scoped to this model's table (soft-delete scope applied at execution). */
    fun newQuery(): ModelBuilder<T> = ModelBuilder(self(), connection().table(table))

    fun newInstance(): T = instantiate(self().javaClass)

    fun newInstance(attributes: Map<String, Any?>): T = newInstance().fill(attributes)

    /** Builds a persisted instance from a database row (no casts or mass-assignment checks). */
    fun newFromBuilder(row: Map<String, Any?>): T {
        val model = newInstance()
        model.attributes.putAll(row)
        model.persisted = true
        model.syncOriginal()
        model.markRetrieved()
        return model
    }

    // ---- relations (storage for relation / eager-load features) ----

    fun getRelation(name: String): Any? = relations[name]

    fun setRelation(name: String, value: Any?): T {
        relations[name] = value
        return self()
    }

    fun relationLoaded(name: String): Boolean = relations.containsKey(name)

    fun getRelations(): Map<String, Any?> = LinkedHashMap(relations)

    /**
     * A loaded relation's value, loading it first when needed (`user.getRelationValue<List<Post>>("posts")`).
     * [name] is the relation method name.
     */
    @Suppress("UNCHECKED_CAST")
    fun <V> getRelationValue(name: String): V? {
        if (!relationLoaded(name)) {
            load(name)
        }
        return relations[name] as V?
    }

    /** Eager loads [relations] onto this model, replacing any loaded values. */
    fun load(vararg relations: String): T {
        EagerLoader.load(listOf(this), *relations)
        return self()
    }

    /** Loads only the [relations] not loaded yet. */
    fun loadMissing(vararg relations: String): T {
        val missing = relations.filter { !relationLoaded(it.substringBefore('.')) }
        return load(*missing.toTypedArray())
    }

    /**
     * Loads `{relation}_count` (or alias) attributes onto this model via a single keyed query.
     * Supports `posts as published_posts_count` aliases.
     */
    fun loadCount(vararg relations: String): T {
        if (!persisted || relations.isEmpty()) {
            return self()
        }
        val counted = newQuery().where(primaryKey, getKey()).withCount(*relations).first() ?: return self()
        copyAggregateAliases(counted, relations) { name, alias -> alias ?: "${name}_count" }
        return self()
    }

    /** Loads `{relation}_exists` (or alias) boolean attributes onto this model. */
    fun loadExists(vararg relations: String): T {
        if (!persisted || relations.isEmpty()) {
            return self()
        }
        val loaded = newQuery().where(primaryKey, getKey()).withExists(*relations).first() ?: return self()
        copyAggregateAliases(loaded, relations) { name, alias -> alias ?: "${name}_exists" }
        return self()
    }

    /**
     * Loads `{relation}_sum_{column}` (or alias) onto this model.
     * [spec] is `relation:column` or `relation as alias:column`.
     */
    fun loadSum(spec: String): T {
        if (!persisted) {
            return self()
        }
        val loaded = newQuery().where(primaryKey, getKey()).withSum(spec).first() ?: return self()
        val (_, alias, _) = parseSumSpec(spec)
        attributes[alias] = loaded.getRawAttribute(alias)
        return self()
    }

    private fun copyAggregateAliases(
        source: T,
        relations: Array<out String>,
        aliasFor: (name: String, alias: String?) -> String,
    ) {
        for (spec in relations) {
            val trimmed = spec.trim()
            val match = COUNT_ALIAS.find(trimmed)
            val name = if (match == null) trimmed else trimmed.substring(0, match.range.first).trim()
            val alias = if (match == null) null else trimmed.substring(match.range.last + 1).trim()
            val key = aliasFor(name, alias)
            attributes[key] = source.getRawAttribute(key)
        }
    }

    // ---- relation definitions (call from a public no-arg method named after the relation) ----

    /** This model owns one [related] row whose [foreignKey] (default `user_id` for `User`) holds our [localKey]. */
    @JvmOverloads
    fun <R : Extract<R>> hasOne(related: Class<R>, foreignKey: String? = null, localKey: String? = null): HasOne<R> =
        HasOne(this, instantiate(related), foreignKey ?: defaultForeignKey(), localKey ?: primaryKey)

    /** This model owns many [related] rows whose [foreignKey] (default `user_id` for `User`) holds our [localKey]. */
    @JvmOverloads
    fun <R : Extract<R>> hasMany(related: Class<R>, foreignKey: String? = null, localKey: String? = null): HasMany<R> =
        HasMany(this, instantiate(related), foreignKey ?: defaultForeignKey(), localKey ?: primaryKey)

    /**
     * Our [foreignKey] (default `user_id` for related `User`) points at the [related] row's [ownerKey].
     * [relation] (default `user`) is the name [BelongsTo.associate] stores the owner under.
     */
    @JvmOverloads
    fun <R : Extract<R>> belongsTo(
        related: Class<R>,
        foreignKey: String? = null,
        ownerKey: String? = null,
        relation: String? = null,
    ): BelongsTo<R> {
        val owner = instantiate(related)
        return BelongsTo(
            this,
            owner,
            foreignKey ?: owner.defaultForeignKey(),
            ownerKey ?: owner.primaryKey,
            relation ?: (related.simpleName.take(1).lowercase() + related.simpleName.drop(1)),
        )
    }

    /**
     * Many-to-many through [table] (default: both snake names sorted and joined, `role_user`)
     * with [foreignPivotKey] (`user_id`) and [relatedPivotKey] (`role_id`).
     */
    @JvmOverloads
    fun <R : Extract<R>> belongsToMany(
        related: Class<R>,
        table: String? = null,
        foreignPivotKey: String? = null,
        relatedPivotKey: String? = null,
        parentKey: String? = null,
        relatedKey: String? = null,
    ): BelongsToMany<R> {
        val other = instantiate(related)
        val pivot = table ?: listOf(Relation.snake(javaClass.simpleName), Relation.snake(related.simpleName)).sorted().joinToString("_")
        return BelongsToMany(
            this,
            other,
            pivot,
            foreignPivotKey ?: defaultForeignKey(),
            relatedPivotKey ?: other.defaultForeignKey(),
            parentKey ?: primaryKey,
            relatedKey ?: other.primaryKey,
        )
    }

    /** `user_id` for a `User` keyed by `id`. */
    fun defaultForeignKey(): String = Relation.snake(javaClass.simpleName) + "_" + primaryKey

    /** Morph alias for this model (`Relation.morphMap` entry or simple class name). */
    open fun getMorphClass(): String = MorphMap.morphAlias(javaClass)

    /**
     * Polymorphic one-to-many: related rows store [name]_type / [name]_id pointing at this model.
     * Defaults: `commentable` → `commentable_type` / `commentable_id`.
     */
    fun <R : Extract<R>> morphMany(related: Class<R>, name: String): MorphMany<R> =
        morphMany(related, name, null, null, null)

    fun <R : Extract<R>> morphMany(related: Class<R>, name: String, type: String?): MorphMany<R> =
        morphMany(related, name, type, null, null)

    fun <R : Extract<R>> morphMany(related: Class<R>, name: String, type: String?, id: String?): MorphMany<R> =
        morphMany(related, name, type, id, null)

    /** Polymorphic one-to-many: related rows store [name]_type / [name]_id pointing at this model. */
    fun <R : Extract<R>> morphMany(
        related: Class<R>,
        name: String,
        type: String?,
        id: String?,
        localKey: String?,
    ): MorphMany<R> {
        return MorphMany(
            this,
            instantiate(related),
            id ?: "${name}_id",
            type ?: "${name}_type",
            getMorphClass(),
            localKey ?: primaryKey,
        )
    }

    fun <R : Extract<R>> morphOne(related: Class<R>, name: String): MorphOne<R> =
        morphOne(related, name, null, null, null)

    fun <R : Extract<R>> morphOne(related: Class<R>, name: String, type: String?): MorphOne<R> =
        morphOne(related, name, type, null, null)

    fun <R : Extract<R>> morphOne(related: Class<R>, name: String, type: String?, id: String?): MorphOne<R> =
        morphOne(related, name, type, id, null)

    /** Polymorphic one-to-one (same columns as [morphMany]). */
    fun <R : Extract<R>> morphOne(
        related: Class<R>,
        name: String,
        type: String?,
        id: String?,
        localKey: String?,
    ): MorphOne<R> {
        return MorphOne(
            this,
            instantiate(related),
            id ?: "${name}_id",
            type ?: "${name}_type",
            getMorphClass(),
            localKey ?: primaryKey,
        )
    }

    fun morphTo(name: String): MorphTo = morphTo(name, null, null)

    fun morphTo(name: String, type: String?): MorphTo = morphTo(name, type, null)

    /** Inverse morph: this model holds `{name}_type` / `{name}_id` pointing at the owner. */
    fun morphTo(name: String, type: String?, id: String?): MorphTo {
        return MorphTo(
            this,
            type ?: "${name}_type",
            id ?: "${name}_id",
            name,
        )
    }

    fun <R : Extract<R>, I : Extract<I>> hasManyThrough(related: Class<R>, through: Class<I>): HasManyThrough<R, I> =
        hasManyThrough(related, through, null, null, null, null)

    fun <R : Extract<R>, I : Extract<I>> hasManyThrough(
        related: Class<R>,
        through: Class<I>,
        firstKey: String?,
    ): HasManyThrough<R, I> = hasManyThrough(related, through, firstKey, null, null, null)

    fun <R : Extract<R>, I : Extract<I>> hasManyThrough(
        related: Class<R>,
        through: Class<I>,
        firstKey: String?,
        secondKey: String?,
    ): HasManyThrough<R, I> = hasManyThrough(related, through, firstKey, secondKey, null, null)

    fun <R : Extract<R>, I : Extract<I>> hasManyThrough(
        related: Class<R>,
        through: Class<I>,
        firstKey: String?,
        secondKey: String?,
        localKey: String?,
    ): HasManyThrough<R, I> = hasManyThrough(related, through, firstKey, secondKey, localKey, null)

    /**
     * Distant one-to-many through an intermediate model
     * (`User.hasManyThrough(Comment::class.java, Post::class.java)`).
     */
    fun <R : Extract<R>, I : Extract<I>> hasManyThrough(
        related: Class<R>,
        through: Class<I>,
        firstKey: String?,
        secondKey: String?,
        localKey: String?,
        secondLocalKey: String?,
    ): HasManyThrough<R, I> {
        val far = instantiate(related)
        val mid = instantiate(through)
        return HasManyThrough(
            this,
            far,
            mid,
            firstKey ?: defaultForeignKey(),
            secondKey ?: mid.defaultForeignKey(),
            localKey ?: primaryKey,
            secondLocalKey ?: mid.primaryKey,
        )
    }

    fun <R : Extract<R>, I : Extract<I>> hasOneThrough(related: Class<R>, through: Class<I>): HasOneThrough<R, I> =
        hasOneThrough(related, through, null, null, null, null)

    fun <R : Extract<R>, I : Extract<I>> hasOneThrough(
        related: Class<R>,
        through: Class<I>,
        firstKey: String?,
    ): HasOneThrough<R, I> = hasOneThrough(related, through, firstKey, null, null, null)

    fun <R : Extract<R>, I : Extract<I>> hasOneThrough(
        related: Class<R>,
        through: Class<I>,
        firstKey: String?,
        secondKey: String?,
    ): HasOneThrough<R, I> = hasOneThrough(related, through, firstKey, secondKey, null, null)

    fun <R : Extract<R>, I : Extract<I>> hasOneThrough(
        related: Class<R>,
        through: Class<I>,
        firstKey: String?,
        secondKey: String?,
        localKey: String?,
    ): HasOneThrough<R, I> = hasOneThrough(related, through, firstKey, secondKey, localKey, null)

    /** Distant one-to-one through an intermediate model. */
    fun <R : Extract<R>, I : Extract<I>> hasOneThrough(
        related: Class<R>,
        through: Class<I>,
        firstKey: String?,
        secondKey: String?,
        localKey: String?,
        secondLocalKey: String?,
    ): HasOneThrough<R, I> {
        val far = instantiate(related)
        val mid = instantiate(through)
        return HasOneThrough(
            this,
            far,
            mid,
            firstKey ?: defaultForeignKey(),
            secondKey ?: mid.defaultForeignKey(),
            localKey ?: primaryKey,
            secondLocalKey ?: mid.primaryKey,
        )
    }

    // ---- serialization ----

    fun makeHidden(vararg keys: String): T {
        val bag = hiddenOverrides
        if (bag == null) {
            val created = linkedSetOf<String>()
            hiddenOverrides = created
            created.addAll(keys)
        } else {
            bag.addAll(keys)
        }
        return self()
    }

    fun makeVisible(vararg keys: String): T {
        val bag = visibleOverrides
        if (bag == null) {
            val created = linkedSetOf<String>()
            visibleOverrides = created
            created.addAll(keys)
        } else {
            bag.addAll(keys)
        }
        val hidden = hiddenOverrides
        if (hidden != null) {
            hidden.removeAll(keys.toSet())
        }
        return self()
    }

    fun setAppends(vararg keys: String): T {
        appendOverrides = linkedSetOf(*keys)
        return self()
    }

    fun effectiveHidden(): Set<String> {
        val base = hidden.toMutableSet()
        hiddenOverrides?.let { base.addAll(it) }
        visibleOverrides?.let { base.removeAll(it) }
        return base
    }

    fun effectiveVisible(): Set<String> {
        val base = visible.toMutableSet()
        visibleOverrides?.let { base.addAll(it) }
        return base
    }

    fun effectiveAppends(): Set<String> {
        val base = appends.toMutableSet()
        appendOverrides?.let { base.clear(); base.addAll(it) }
        return base
    }

    /** Override to resolve appended attributes (`getFullNameAttribute` shape). */
    open fun getAppendedAttribute(key: String): Any? = null

    /**
     * Cast attribute values plus loaded relations (models rendered recursively),
     * respecting [hidden], [visible], and [appends].
     */
    open fun toMap(): Map<String, Any?> = toArray()

    /** Laravel `toArray()` alias for model serialization. */
    open fun toArray(): Map<String, Any?> {
        val result = linkedMapOf<String, Any?>()
        val visibleKeys = effectiveVisible()
        val hiddenKeys = effectiveHidden()
        attributes.keys.forEach { key ->
            if (shouldSerialize(key, visibleKeys, hiddenKeys)) {
                result[key] = getAttribute(key)
            }
        }
        relations.forEach { (name, value) ->
            if (!shouldSerialize(name, visibleKeys, hiddenKeys)) {
                return@forEach
            }
            result[name] = when (value) {
                is Extract<*> -> value.toArray()
                is Collection<*> -> value.map { if (it is Extract<*>) it.toArray() else it }
                else -> value
            }
        }
        for (append in effectiveAppends()) {
            result[append] = getAppendedAttribute(append)
        }
        return result
    }

    /** JSON serialization of [toArray]. */
    open fun toJson(): String {
        val mapper = com.fasterxml.jackson.databind.ObjectMapper()
        return mapper.writeValueAsString(toArray())
    }

    private fun shouldSerialize(key: String, visibleKeys: Set<String>, hiddenKeys: Set<String>): Boolean {
        if (visibleKeys.isNotEmpty()) {
            return key in visibleKeys
        }
        return key !in hiddenKeys
    }

    override fun toString(): String = javaClass.simpleName + attributes

    internal fun performForceDelete(): Boolean {
        if (!persisted) {
            return false
        }
        ModelDispatcher.dispatch(ModelEvent.FORCE_DELETING, this)
        keyQuery().delete()
        persisted = false
        ModelDispatcher.dispatch(ModelEvent.FORCE_DELETED, this)
        return true
    }

    internal fun performRestore(column: String): Boolean {
        ModelDispatcher.dispatch(ModelEvent.RESTORING, this)
        attributes[column] = null
        val saved = save()
        ModelDispatcher.dispatch(ModelEvent.RESTORED, this)
        return saved
    }

    internal fun markRetrieved() {
        ModelDispatcher.dispatch(ModelEvent.RETRIEVED, this)
    }

    private fun runSoftDelete(column: String) {
        val now = freshTimestamp()
        val values = linkedMapOf<String, Any?>(column to now)
        if (timestamps) {
            values[updatedAtColumn] = now
        }
        keyQuery().update(values)
        attributes.putAll(values)
        original = original + values
    }

    private fun touchTimestamps() {
        val now = freshTimestamp()
        if (!isDirty(updatedAtColumn)) {
            attributes[updatedAtColumn] = now
        }
        if (!persisted && !isDirty(createdAtColumn)) {
            attributes[createdAtColumn] = now
        }
    }

    private fun keyQuery(): QueryBuilder {
        val key = getKey() ?: throw ExtractException("Model [${javaClass.name}] has no [$primaryKey] value.")
        return connection().table(table).where(primaryKey, key)
    }

    companion object {
        private val Y_PLURAL = Regex("[^aeiou]y$")
        private val SIBILANT = Regex("(s|x|z|ch|sh)$")
        private val CAMEL = Regex("([a-z0-9])([A-Z])")
        private val COUNT_ALIAS = Regex("\\s+as\\s+", RegexOption.IGNORE_CASE)

        /**
         * Parses `relation:column` or `relation as alias:column` for [loadSum] / [ModelBuilder.withSum].
         * Returns (relationName, selectAlias, column).
         */
        internal fun parseSumSpec(spec: String): Triple<String, String, String> {
            val trimmed = spec.trim()
            val colon = trimmed.lastIndexOf(':')
            require(colon > 0) {
                "withSum/loadSum expects \"relation:column\" or \"relation as alias:column\", got [$spec]"
            }
            val left = trimmed.substring(0, colon).trim()
            val column = trimmed.substring(colon + 1).trim()
            require(column.isNotEmpty()) { "withSum/loadSum column is empty in [$spec]" }
            val match = COUNT_ALIAS.find(left)
            return if (match == null) {
                Triple(left, "${left}_sum_$column", column)
            } else {
                val name = left.substring(0, match.range.first).trim()
                val alias = left.substring(match.range.last + 1).trim()
                Triple(name, alias, column)
            }
        }

        /** Static helpers for a model class (Java: `Extract.of(User.class).find(1)`). */
        @JvmStatic
        fun <T : Extract<T>> of(type: Class<T>): ExtractCompanion<T> = ExtractCompanion(type)

        /** `BlogPost` → `blog_posts`, `Category` → `categories`, `Box` → `boxes`. */
        @JvmStatic
        fun tableNameFor(className: String): String {
            val snake = CAMEL.replace(className, "$1_$2").lowercase()
            return when {
                Y_PLURAL.containsMatchIn(snake) -> snake.dropLast(1) + "ies"
                SIBILANT.containsMatchIn(snake) -> snake + "es"
                else -> snake + "s"
            }
        }

        internal fun <M> instantiate(type: Class<M>): M = try {
            val constructor = type.getDeclaredConstructor()
            constructor.isAccessible = true
            constructor.newInstance()
        } catch (e: ReflectiveOperationException) {
            throw ExtractException("Cannot instantiate model [${type.name}]; it needs a no-arg constructor.", e)
        }
    }
}
