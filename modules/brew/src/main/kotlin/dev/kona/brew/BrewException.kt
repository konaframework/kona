package dev.kona.brew

/**
 * Failures while resolving, restoring, or invoking Brew components.
 */
class BrewException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
