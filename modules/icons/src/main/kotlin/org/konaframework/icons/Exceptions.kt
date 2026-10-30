package org.konaframework.icons

/** Thrown when an icon cannot be resolved from any registered set. */
class IconNotFoundException(name: String) : RuntimeException("Icon [$name] not found")

/** Thrown when an icon set cannot be registered. */
class CannotRegisterIconSetException(message: String) : RuntimeException(message)
