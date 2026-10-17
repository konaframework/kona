package org.konaframework.http

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HeaderMapsTest {
    @Test
    fun mapsFirstValues() {
        assertEquals(
            mapOf("A" to "1", "B" to "2"),
            HeaderMaps.from(mapOf("A" to listOf("1", "x"), "B" to listOf("2"))),
        )
    }
}
