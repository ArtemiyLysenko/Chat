package edu.artemiy.chat.attachments.spi;

import java.net.URI;

public interface AttachmentStoragePort {

    URI store(String storageKey, byte[] content);

    boolean exists(String storageKey);
}
