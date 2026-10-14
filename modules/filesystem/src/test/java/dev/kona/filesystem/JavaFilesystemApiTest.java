package dev.kona.filesystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    }
}
