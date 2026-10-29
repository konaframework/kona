package org.konaframework.firefly.forms

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.support.FireflyException

class SelectSearchUrlTest {
    @Test
    fun searchUrlPointsTheComboboxAtARemoteEndpoint() {
        val plain = Select.make("recordId").options(mapOf("1" to "Ada"))
        assertNull(plain.searchUrl())
        assertFalse(Schema.make().components(plain).render().contains("data-ff-search-url"))
        val remote = Select.make("recordId").searchable(false).searchUrl("/admin/projects/relations/options?relation=tasks&op=attach")
        assertEquals("/admin/projects/relations/options?relation=tasks&op=attach", remote.searchUrl())
        val html = Schema.make().components(remote).render()
        assertTrue(html.contains("""data-ff-search-url="/admin/projects/relations/options?relation=tasks&amp;op=attach""""), html)
        assertTrue(html.contains("""data-ff-searchable="true""""), "a search url makes the select searchable")
        assertThrows(FireflyException::class.java) { Select.make("recordId").searchUrl(" ") }
    }
}
