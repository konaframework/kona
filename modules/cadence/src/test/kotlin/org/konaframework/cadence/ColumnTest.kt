package org.konaframework.cadence

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ColumnUser : Cadence<ColumnUser>() {
    var name: String? by column()
    var email: String? by column("email_address")
    var nickname: String? by attr()
}

class ColumnTest {
    @Test
    fun `column and attr properties are compiler-visible attributes`() {
        val user = ColumnUser()
        assertNull(user.name)
        user.name = "Ada"
        user.email = "ada@example.com"
        user.nickname = "A"
        assertEquals("Ada", user.name)
        assertEquals("Ada", user.getAttribute("name"))
        assertEquals("ada@example.com", user.getAttribute("email_address"))
        assertEquals("ada@example.com", user.email)
        assertEquals("A", user.getAttribute("nickname"))
        val found: ColumnUser? = user
        if (found != null) {
            assertEquals("Ada", found.name)
        }
    }
}
