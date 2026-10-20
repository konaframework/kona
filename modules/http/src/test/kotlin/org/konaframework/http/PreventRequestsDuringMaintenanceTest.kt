package org.konaframework.http

import org.konaframework.core.Application
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class PreventRequestsDuringMaintenanceTest {
    @Test
    fun fileDriverReturns503WhenDownExists(@TempDir base: Path) {
        val mw = PreventRequestsDuringMaintenance(base, "file")
        assertFalse(mw.active())
        val live = mw.handle(Request.get("/")) { Response.text("ok") }
        assertEquals(200, live.status)

        val down = PreventRequestsDuringMaintenance.downFile(base)
        Files.createDirectories(down.parent)
        Files.writeString(down, "{}\n")
        assertTrue(mw.active())
        val blocked = mw.handle(Request.get("/")) { Response.text("ok") }
        assertEquals(503, blocked.status)
        assertEquals("60", blocked.headers["Retry-After"])
    }

    @Test
    fun nonFileDriverIsInactive(@TempDir base: Path) {
        Files.createDirectories(PreventRequestsDuringMaintenance.downFile(base).parent)
        Files.writeString(PreventRequestsDuringMaintenance.downFile(base), "{}\n")
        val cache = PreventRequestsDuringMaintenance(base, "cache")
        assertFalse(cache.active())
        assertEquals(200, cache.handle(Request.get("/")) { Response.text("ok") }.status)

        val blank = PreventRequestsDuringMaintenance(base, "")
        assertTrue(blank.active())
    }

    @Test
    fun providerPrependsMaintenanceMiddleware(@TempDir base: Path) {
        val app = Application(base)
        app.config.set("logging.to_file", false)
        app.register(HttpServiceProvider()).boot()
        val kernel = app.resolve(HttpKernel::class.java)
        assertTrue(kernel.globalMiddleware.any { it is PreventRequestsDuringMaintenance })
        HttpServiceProvider().boot(app)
        assertEquals(1, kernel.globalMiddleware.count { it is PreventRequestsDuringMaintenance })

        val fromApp = PreventRequestsDuringMaintenance(app)
        assertFalse(fromApp.active())
    }
}
