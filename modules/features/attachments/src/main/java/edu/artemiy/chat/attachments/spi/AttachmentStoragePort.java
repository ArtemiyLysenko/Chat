package edu.artemiy.chat.attachments.spi;

public interface AttachmentStoragePort {

    void store(String storageKey, byte[] content);

    byte[] read(String storageKey);

    void delete(String storageKey);

    boolean exists(String storageKey);
}
