package org.konaframework.support.date

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule

/** Jackson module: [KonaDate] ↔ ISO-8601 string. */
fun konaDateJacksonModule(): SimpleModule =
    SimpleModule("KonaDate").apply {
        addSerializer(KonaDate::class.java, KonaDateJsonSerializer())
        addDeserializer(KonaDate::class.java, KonaDateJsonDeserializer())
    }

internal class KonaDateJsonSerializer : JsonSerializer<KonaDate>() {
    override fun serialize(value: KonaDate, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeString(value.toIso8601String())
    }
}

internal class KonaDateJsonDeserializer : JsonDeserializer<KonaDate>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): KonaDate =
        KonaDate.parse(p.valueAsString)
}
