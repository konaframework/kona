package io.konaframework.filesystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.konaframework.core.Config;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class JavaFilesystemApiTest {
    @Test
    void localMemoryScopedAndTemporaryUrlsFromJava(@TempDir Path base) throws Exception {
        LocalAdapter local = new LocalAdapter(base.resolve("local"));
        local.put("a.txt", "hi");
        assertEquals("hi", local.getString("a.txt"));
        assertTrue(local.exists("a.txt"));
        assertTrue(local.missing("nope.txt"));
        local.append("a.txt", "!");
        assertEquals("hi!", local.getString("a.txt"));

        S3Adapter s3 = new S3Adapter(new MemoryS3ClientAdapter(), "bucket");
        s3.put("b.txt", "bye");
        assertEquals("bye", s3.getString("b.txt"));

        ScopedAdapter scoped = new ScopedAdapter(local, "nest");
        scoped.put("c.txt", "c");
        assertEquals("c", local.getString("nest/c.txt"));

        ReadOnlyAdapter readOnly = new ReadOnlyAdapter(local);
        assertEquals("hi!", readOnly.getString("a.txt"));

        Config config = new Config();
        config.set("filesystems.disks.videos.driver", "scoped");
        config.set("filesystems.disks.videos.disk", "local");
        config.set("filesystems.disks.videos.prefix", "media");
        config.set("filesystems.disks.local.driver", "local");
        config.set("filesystems.disks.local.root", base.resolve("mgr").toString());
        FilesystemManager manager = new FilesystemManager(config, base);
        manager.disk("local").put("media/x.txt", "x");
        assertEquals("x", manager.disk("videos").getString("x.txt"));

        manager.buildTemporaryUrlsUsing("local", (diskUrl, exp) -> diskUrl + "?e=" + exp);
        assertTrue(manager.providesTemporaryUrls("local"));
        String url = manager.temporaryUrl("media/x.txt", 90L, "local");
        assertTrue(url.contains("e=90"));

        String signed = manager.temporaryUrl("photo.png", 90L, (diskUrl, exp) -> diskUrl + "?e=" + exp, "public");
        assertTrue(signed.startsWith("/storage/photo.png?e=90"));

        Path upload = base.resolve("up.bin");
        Files.writeString(upload, "data");
        String stored = local.putFileAs("uploads", upload, "up.bin");
        assertEquals("uploads/up.bin", stored);
        assertFalse(Visibility.PUBLIC.isEmpty());
    }
}
