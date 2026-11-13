package org.konaframework.firefly.infolists

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.konaframework.core.Config
import org.konaframework.filesystem.FilesystemManager
import org.konaframework.filesystem.LocalAdapter
import org.konaframework.filesystem.Visibility
import org.konaframework.firefly.support.FireflyException
import java.nio.file.Path

class ImageDiskTest {
    @AfterEach
    fun clear() {
        ImageEntry.clearDisks()
    }

    @Test
    fun privateDiskSignsATemporaryUrlAndSkipsMissingFiles(@TempDir root: Path) {
        val disk = signedDisk(root)
        disk.put("ada.png", "png")
        disk.put("public.png", "png")
        disk.setVisibility("public.png", Visibility.PUBLIC)
        ImageEntry.useDisks(ImageDisks { disk })

        val privateUrl = html(
            ImageEntry.make("photo").disk("avatars").visibility("private").state("ada.png"),
        )
        assertTrue(privateUrl.contains("/media/ada.png?expires=300"), privateUrl)
        assertTrue(privateUrl.contains("data-ff-disk=\"avatars\""), privateUrl)

        val custom = html(
            ImageEntry.make("photo").disk(" avatars ").visibility("Private").temporaryUrlExpiration(90).state("ada.png"),
        )
        assertTrue(custom.contains("/media/ada.png?expires=90"), custom)

        val fromGetter = html(
            ImageEntry.make("photo").disk("avatars").temporaryUrlExpiration { 45 }.state("ada.png"),
        )
        assertTrue(fromGetter.contains("expires=45"), fromGetter)

        val publicUrl = html(
            ImageEntry.make("photo").disk("avatars").visibility("public").state("ada.png"),
        )
        assertTrue(publicUrl.contains("src=\"/media/ada.png\""), publicUrl)
        assertTrue(!publicUrl.contains("expires="), publicUrl)

        val storedPublic = html(
            ImageEntry.make("photo").disk("avatars").state("public.png"),
        )
        assertTrue(storedPublic.contains("src=\"/media/public.png\""), storedPublic)

        val missing = html(
            ImageEntry.make("photo").disk("avatars").defaultImageUrl("/fallback.png").state("missing.png"),
        )
        assertTrue(missing.contains("/fallback.png"), missing)
        val dropped = html(ImageEntry.make("photo").disk("avatars").placeholder("gone").state("missing.png"))
        assertTrue(dropped.contains("ff-placeholder\">gone<"), dropped)
        val emptyFallback = html(
            ImageEntry.make("photo").disk("avatars").defaultImageUrl("").placeholder("none").state("missing.png"),
        )
        assertTrue(emptyFallback.contains("ff-placeholder\">none<"), emptyFallback)

        val unchecked = html(
            ImageEntry.make("photo").disk("avatars").visibility("public").checkFileExistence(false).state("missing.png"),
        )
        assertTrue(unchecked.contains("src=\"/media/missing.png\""), unchecked)

        val remote = html(
            ImageEntry.make("photo").disk("avatars").state(
                listOf(
                    "https://example.test/a.png",
                    "http://example.test/b.png",
                    "data:image/png;base64,aaaa",
                    "//cdn.example.test/c.png",
                ),
            ),
        )
        assertTrue(remote.contains("https://example.test/a.png"), remote)
        assertTrue(remote.contains("http://example.test/b.png"), remote)
        assertTrue(remote.contains("data:image/png;base64,aaaa"), remote)
        assertTrue(remote.contains("//cdn.example.test/c.png"), remote)

        val blankDisk = html(ImageEntry.make("photo").disk("  ").state("ada.png"))
        assertTrue(blankDisk.contains("src=\"ada.png\""), blankDisk)
        val spaced = html(
            ImageEntry.make("photo").disk("avatars").checkFileExistence(false).state("   "),
        )
        assertTrue(spaced.contains("src=\"   \""), spaced)

        assertThrows(FireflyException::class.java) { ImageEntry.make("photo").temporaryUrlExpiration(0) }
        assertThrows(FireflyException::class.java) {
            html(ImageEntry.make("photo").disk("avatars").visibility("private").temporaryUrlExpiration { 0 }.state("ada.png"))
        }
    }

    @Test
    fun managerOpensTheNamedDiskAndUnsignedDisksFail(@TempDir root: Path) {
        val signed = signedDisk(root.resolve("signed"))
        signed.put("ada.png", "png")
        val manager = FilesystemManager(Config(), root)
        manager.set("avatars", signed)
        ImageEntry.useDisks(manager)
        val html = html(ImageEntry.make("photo").disk("avatars").visibility("private").state("ada.png"))
        assertTrue(html.contains("/media/ada.png?expires=300"), html)

        ImageEntry.clearDisks()
        val raw = html(ImageEntry.make("photo").disk("avatars").state("ada.png"))
        assertTrue(raw.contains("src=\"ada.png\""), raw)

        val unsigned = LocalAdapter(root.resolve("plain"), "/media")
        unsigned.put("ada.png", "png")
        ImageEntry.useDisks(ImageDisks { unsigned })
        assertThrows(FireflyException::class.java) {
            html(ImageEntry.make("photo").disk("avatars").visibility("private").state("ada.png"))
        }
    }
}

private fun signedDisk(root: Path): LocalAdapter {
    val disk = LocalAdapter(root, "/media")
    disk.buildTemporaryUrlsUsing { url, exp -> "$url?expires=$exp" }
    return disk
}

private fun html(entry: ImageEntry): String = Infolist.make().components(entry).render()
