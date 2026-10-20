package org.konaframework.lsp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HostMembersTest {
    @Test
    fun javaAndKotlinMembersAndPrefix() {
        val src =
            """
            public class Counter {
                public int count = 0;
                public String label;
                protected int other = 2;
                public void increment() {}
                public int doubled() { return count * 2; }
                public String template() { return "<div></div>"; }
                public Counter() {}
                private int hidden = 1;
                class Inner {
                    public int innerField = 1;
                    public void innerMethod() {}
                }
            }
            """.trimIndent()
        val offset = src.indexOf("template")
        val names = HostMembers.members(src, offset).map { it.name }
        assertTrue(names.contains("count"))
        assertTrue(names.contains("increment"))
        assertTrue(names.contains("doubled"))
        assertFalse(names.contains("template"))
        assertFalse(names.contains("innerField"))
        assertFalse(names.contains("Counter"))
        assertEquals(listOf("count"), HostMembers.complete(src, offset, "cou").map { it.name })
        assertTrue(HostMembers.complete(src, offset, "").size >= 3)
        assertTrue(HostMembers.members(src, 0).isEmpty())
        assertNull(HostMembers.enclosing(src, 0))

        val kt =
            """
            class Box : Component() {
                var title = "t"
                val kind = "k"
                fun save() {}
                override fun template() = "x"
                object Nested { val skip = 1 }
            }
            """.trimIndent()
        val ktOffset = kt.indexOf("template")
        val ktNames = HostMembers.members(kt, ktOffset).map { it.name }
        assertTrue(ktNames.contains("title"))
        assertTrue(ktNames.contains("kind"))
        assertTrue(ktNames.contains("save"))
        assertFalse(ktNames.contains("skip"))
        assertFalse(ktNames.contains("template"))
    }

    @Test
    fun classShapesAndUnclosedBody() {
        assertTrue(HostMembers.classSpans("class ").isEmpty())
        assertTrue(HostMembers.classSpans("class 123 {}").isEmpty())
        assertTrue(HostMembers.classSpans("class Foo").isEmpty())
        val obj = HostMembers.classSpans("object Foo { val a = 1 }")
        assertEquals("Foo", obj.single().name)
        val iface = HostMembers.classSpans("interface Bar { }")
        assertEquals("Bar", iface.single().name)
        val unclosed = "class Foo {"
        assertEquals(1, HostMembers.classSpans(unclosed).size)
        assertTrue(HostMembers.members(unclosed, unclosed.lastIndex).isEmpty())
        val withString = HostMembers.classSpans("class Foo : Bar(\"{\") { int x; }")
        assertEquals("Foo", withString.single().name)
    }
}
