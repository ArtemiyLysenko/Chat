package edu.artemiy.chat.adapters.persistence.jpa;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

import edu.artemiy.chat.attachments.spi.AttachmentStoragePort;

@SpringBootConfiguration
@EnableAutoConfiguration
@Import(PersistenceJpaConfiguration.class)
@ComponentScan("edu.artemiy.chat.adapters.persistence.jpa")
public class PersistenceJpaTestApplication {

    @Bean
    AttachmentStoragePort attachmentStoragePort() {
        return new AttachmentStoragePort() {
            @Override
            public void store(String storageKey, byte[] content) {
            }

            @Override
            public byte[] read(String storageKey) {
                return new byte[0];
            }

            @Override
            public void delete(String storageKey) {
            }

            @Override
            public boolean exists(String storageKey) {
                return false;
            }
        };
    }
}
