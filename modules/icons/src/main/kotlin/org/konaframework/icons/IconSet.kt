package org.konaframework.icons

import java.nio.file.Path

/**
 * Options for [Icons.add] (Blade Icons `Factory::add` shape).
 *
 * Provide at least one of [path]/[paths] (filesystem) or [classpath]/[classpaths]
 * (classpath resource directories, no leading slash).
 */
data class IconSetOptions @JvmOverloads constructor(
    val prefix: String,
    val path: Path? = null,
    val paths: List<Path> = emptyList(),
    val classpath: String? = null,
    val classpaths: List<String> = emptyList(),
    /** Default CSS class applied to every icon from this set. */
    val className: String? = null,
    /** Default HTML attributes merged onto the root `<svg>`. */
    val attributes: Map<String, String> = emptyMap(),
    /** Icon name (without set prefix) used when the requested icon is missing. */
    val fallback: String? = null,
)

/** Registered icon set. */
data class IconSet(
    val name: String,
    val prefix: String,
    val paths: List<Path>,
    val classpaths: List<String>,
    val className: String?,
    val attributes: Map<String, String>,
    val fallback: String?,
)
