package edu.artemiy.chat.adapters.storage.filesystem;

import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import edu.artemiy.chat.attachments.spi.AttachmentStoragePort;

public final class FilesystemAttachmentStorage implements AttachmentStoragePort {

    private final Path storageRoot;

    public FilesystemAttachmentStorage(Path storageRoot) {
        this.storageRoot = storageRoot;
    }

    @Override
    public URI store(String storageKey, byte[] content) {
        Path target = storageRoot.resolve(storageKey);
        try {
            Files.createDirectories(target.getParent() == null ? storageRoot : target.getParent());
            Files.write(target, content);
        } catch (java.io.IOException exception) {
            throw new UncheckedIOException("Unable to store attachment " + storageKey, exception);
        }
        return target.toUri();
    }

    @Override
    public boolean exists(String storageKey) {
        return Files.exists(storageRoot.resolve(storageKey));
    }
}
