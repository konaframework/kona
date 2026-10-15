package dev.kona.filesystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.kona.core.Config;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class JavaFilesystemApiTest {
    @Test
    void localAndMemoryDisksFromJava(@TempDir Path base) {
        LocalAdapter local = new LocalAdapter(base.resolve("local"));
        local.put("a.txt", "hi");
        assertEquals("hi", local.getString("a.txt"));
        assertTrue(local.exists("a.txt"));

        S3Adapter s3 = new S3Adapter(new MemoryS3ClientAdapter(), "bucket");
        s3.put("b.txt", "bye");
        assertEquals("bye", s3.getString("b.txt"));

        FilesystemManager manager = new FilesystemManager(new Config(), base);
        String url = manager.temporaryUrl("photo.png", 90L, (diskUrl, exp) -> diskUrl + "?e=" + exp, "public");
        assertTrue(url.startsWith("/storage/photo.png?e=90"));
    }
}
