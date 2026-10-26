package org.konaframework.cadence

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Typed column on a [Cadence] model.
 *
 * ```kotlin
 * class User : Cadence<User>() {
 *     var name: String? by column()
 *     var email: String? by column("email_address")
 * }
 * val user = User.first()
 * if (user != null) {
 *     user.name
 *     user.email
 * }
 * ```
 *
 * The property name is the column name unless [key] is set. Reads and writes go through
 * [Cadence.getAttribute] / [Cadence.setAttribute], so casts and mutators still apply.
 * Java callers use the generated `getName` / `setName` methods.
 */
class Column<V>(private val key: String?) : ReadWriteProperty<Cadence<*>, V> {
    override fun getValue(thisRef: Cadence<*>, property: KProperty<*>): V {
        @Suppress("UNCHECKED_CAST")
        return thisRef.getAttribute(columnName(property)) as V
    }

    override fun setValue(thisRef: Cadence<*>, property: KProperty<*>, value: V) {
        thisRef.setAttribute(columnName(property), value)
    }

    private fun columnName(property: KProperty<*>): String = key ?: property.name
}

/** Column delegate. [key] overrides the property name (`email` stored as `email_address`). */
fun <V> column(key: String? = null): Column<V> = Column(key)

/** Alias of [column]. */
fun <V> attr(key: String? = null): Column<V> = column(key)
