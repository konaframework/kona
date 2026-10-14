package dev.kona.core

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Simple logging facade (Laravel Log shape).
 */
object Log {
    private val logger: Logger = LoggerFactory.getLogger("kona")

    @JvmStatic
    fun info(message: String) = logger.info(message)

    @JvmStatic
    fun debug(message: String) = logger.debug(message)

    @JvmStatic
    fun warn(message: String) = logger.warn(message)

    @JvmStatic
    fun error(message: String) = logger.error(message)

    @JvmStatic
    fun error(message: String, throwable: Throwable) = logger.error(message, throwable)
}
