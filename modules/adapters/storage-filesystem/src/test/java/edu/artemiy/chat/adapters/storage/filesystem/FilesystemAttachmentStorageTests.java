package edu.artemiy.chat.adapters.storage.filesystem;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FilesystemAttachmentStorageTests {

    @TempDir
    Path tempDir;

    @Test
    void storesAttachmentsUnderTheConfiguredRoot() throws Exception {
        FilesystemAttachmentStorage storage = new FilesystemAttachmentStorage(tempDir);

        storage.store("chat/test.txt", "hello".getBytes());

        assertThat(storage.exists("chat/test.txt")).isTrue();
        assertThat(Files.readString(tempDir.resolve("chat/test.txt"))).isEqualTo("hello");
    }
}
