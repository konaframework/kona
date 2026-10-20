package org.konaframework.support.json

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.konaframework.support.date.konaDateJacksonModule

/**
 * Shared Jackson mapper for HTTP JSON responses and other framework JSON.
 *
 * Java Time types and [org.konaframework.support.date.KonaDate] serialize as ISO-8601 strings,
 * not numeric timestamps.
 */
object Json {
    /** Configured mapper: Java Time + [KonaDate], ISO-8601 strings. */
    @JvmField
    val mapper: ObjectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .registerModule(konaDateJacksonModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    @JvmStatic
    fun stringify(value: Any?): String = mapper.writeValueAsString(value)
}
