package dev.kona.core

/**
 * Marks thin JVM-bridge wrappers that Kover cannot fully attribute when they only
 * delegate to Java implementations. Real logic lives in the Java helpers and remains
 * under the 100% coverage gate.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
internal annotation class KoverExclude
