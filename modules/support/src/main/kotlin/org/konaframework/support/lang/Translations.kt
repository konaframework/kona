@file:JvmName("Translations")

package org.konaframework.support.lang

/**
 * Top-level localization helpers matching [Lang.get].
 *
 * Java callers use `Translations.__("key")` (see [JvmName]).
 * Kotlin callers use [translate] (the name `__` is reserved in Kotlin).
 */
@JvmName("__")
fun translate(key: String): String = Lang.get(key)

@JvmName("__")
fun translate(key: String, replace: Map<String, Any?>): String = Lang.get(key, replace)
