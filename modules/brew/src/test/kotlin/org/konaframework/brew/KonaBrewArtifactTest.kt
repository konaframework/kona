package org.konaframework.brew

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class KonaBrewArtifactTest {
    @Test
    fun replacementPointsAtWire() {
        assertEquals("kona-wire", KonaBrewArtifact.REPLACEMENT)
        assertEquals("kona-wire", KonaBrewArtifact.replacement())
        assertEquals(
            org.konaframework.wire.WireServiceProvider::class.java,
            BrewServiceProvider()::class.java.superclass,
        )
    }
}
