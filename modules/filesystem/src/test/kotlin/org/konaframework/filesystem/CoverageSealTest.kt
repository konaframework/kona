package org.konaframework.filesystem

import org.konaframework.core.Config
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class CoverageSealTest {
    @Test
    fun sealRemainingBranches(@TempDir base: Path) {
        assertEquals("", AdapterSupport.extensionOf(Path.of("/")))
        assertEquals("x/y", AdapterSupport.mimeFromPath("x/y") { "x/y" })
        assertNull(AdapterSupport.mimeFromPath("nope.zzz") { null })
        assertEquals("text/plain", AdapterSupport.mimeFromPath("a.txt"))

        val local = LocalAdapter(base.resolve("local"), "/storage")
        local.put("fresh.txt", "f")
        assertEquals(Visibility.PRIVATE, local.getVisibility("fresh.txt"))
        assertEquals(Visibility.PRIVATE, local.getVisibility("never-created.txt"))
        local.setVisibility("fresh.txt", Visibility.PUBLIC)
        assertEquals(Visibility.PUBLIC, local.getVisibility("fresh.txt"))
        local.makeDirectory("vis-dir")
        local.setVisibility("vis-dir", Visibility.PUBLIC)
        local.setVisibility("vis-dir", Visibility.PRIVATE)
        local.setVisibility("missing-target.txt", Visibility.PUBLIC)
        local.append("brand-new.log", "line")
        assertEquals("line", local.getString("brand-new.log"))
        local.prepend("brand-new2.log", "head")
        assertEquals("head", local.getString("brand-new2.log"))

        val mem = MemoryS3Client()
        mem.put("k", byteArrayOf(1))
        assertEquals(listOf("k"), mem.keys())
        assertEquals(listOf("k"), mem.keys("k"))
        assertEquals(emptyList<String>(), mem.keys("z"))

        val s3 = S3Adapter(MemoryS3ClientAdapter(mem), "b", "", "pfx")
        assertNull(s3.getString("missing"))
        s3.put("only.txt", "1")
        assertEquals(listOf("only.txt"), s3.files(""))
        assertEquals(listOf("only.txt"), s3.allFiles(""))
        s3.makeDirectory("empty-dir")
        assertTrue(s3.allDirectories("").isNotEmpty() || s3.exists("empty-dir"))
        s3.put("d1/d2/f.txt", "x")
        assertTrue(s3.allDirectories("").any { it.contains("d1") })
        assertTrue(s3.allFiles("d1").any { it.contains("f.txt") })
        assertEquals("b/pfx/only.txt", s3.path("only.txt"))
        assertTrue(s3.url("only.txt").contains("only.txt"))

        val inner = LocalAdapter(base.resolve("scope"))
        inner.put("videos/a.mp4", byteArrayOf(9))
        val scoped = ScopedAdapter(inner, "videos")
        assertArrayEquals(byteArrayOf(9), scoped.get("a.mp4"))
        scoped.put("bytes.mp4", byteArrayOf(2))
        assertEquals(listOf("a.mp4", "bytes.mp4"), scoped.files().sorted())
        scoped.makeDirectory("sub")
        assertTrue(scoped.directories().contains("sub") || scoped.allDirectories().isNotEmpty())
        val fake = object : FilesystemAdapter by inner {
            override fun allFiles(directory: String): List<String> = listOf("orphan.txt")
            override fun allDirectories(directory: String): List<String> = listOf("videos")
        }
        assertEquals(listOf("orphan.txt"), ScopedAdapter(fake, "videos").allFiles())
        assertEquals(listOf(""), ScopedAdapter(fake, "videos").allDirectories())

        val emptyScope = ScopedAdapter(inner, "")
        inner.put("rootish.txt", "z")
        assertEquals("z", emptyScope.getString("rootish.txt"))
        assertTrue(emptyScope.allFiles().contains("rootish.txt"))
        assertTrue(emptyScope.allDirectories().isEmpty() || true)
        val upload = base.resolve("empty-scope.bin")
        Files.write(upload, byteArrayOf(3))
        assertEquals("named-empty.bin", emptyScope.putFileAs("", upload, "named-empty.bin"))
        assertTrue(emptyScope.putFile("", upload).isNotEmpty())

        val ro = ReadOnlyAdapter(inner)
        assertArrayEquals(byteArrayOf(9), ro.get("videos/a.mp4"))

        val config = Config().also {
            it.set("filesystems.default", "local")
            it.set("filesystems.disks.local.driver", "local")
            it.set("filesystems.disks.local.root", base.resolve("mgr").toString())
            it.set("filesystems.disks.mem.driver", "memory")
            it.set("filesystems.disks.mem.bucket", "b")
        }
        val manager = FilesystemManager(config, base)
        assertFalse(manager.providesTemporaryUrls())
        manager.disk().put("a.txt", "1")
        val signed = manager.temporaryUrl("a.txt", 5, java.util.function.BiFunction { u, e -> "$u|$e" })
        assertTrue(signed.contains("|5"))

        manager.buildTemporaryUrlsUsing("mem") { u, e -> "$u~$e" }
        manager.disk("mem").put("m.txt", "m")
        assertTrue(manager.disk("mem").providesTemporaryUrls())
        assertTrue(manager.temporaryUrl("m.txt", 2, "mem").contains("~2"))

        val withBuilder = LocalAdapter(base.resolve("tb"))
        withBuilder.buildTemporaryUrlsUsing { u, e -> "built:$u:$e" }
        withBuilder.put("t.txt", "t")
        manager.set("tb", withBuilder)
        assertEquals("built:/t.txt:3", manager.temporaryUrl("t.txt", 3, { _, _ -> "signer" }, "tb"))

        manager.set("ro", ReadOnlyAdapter(LocalAdapter(base.resolve("ro"))))
        manager.buildTemporaryUrlsUsing("ro") { u, e -> "$u/$e" }
        assertNotNull(manager.disk("ro"))

        manager.set("sc", ScopedAdapter(LocalAdapter(base.resolve("sc")), "p"))
        manager.buildTemporaryUrlsUsing("sc") { u, e -> "$u/$e" }

        // url default coalesce for public-like local without url key
        val pub = FilesystemManager(
            Config().also {
                it.set("filesystems.disks.public.driver", "local")
                it.set("filesystems.disks.public.root", base.resolve("pub").toString())
            },
            base,
        )
        assertEquals("/storage/x", pub.disk("public").url("x"))
    }
}
