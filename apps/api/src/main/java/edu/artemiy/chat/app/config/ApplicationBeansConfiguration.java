package edu.artemiy.chat.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import edu.artemiy.chat.adapters.storage.filesystem.FilesystemAttachmentStorage;
import edu.artemiy.chat.admin.api.AdminFeatureFactory;
import edu.artemiy.chat.admin.api.AdminObservabilityQuery;
import edu.artemiy.chat.attachments.spi.AttachmentStoragePort;
import edu.artemiy.chat.core.kernel.ClockPort;
import edu.artemiy.chat.federation.api.FederationAdminQuery;
import edu.artemiy.chat.federation.api.FederationFeatureFactory;

@Configuration
class ApplicationBeansConfiguration {

    @Bean
    ClockPort clockPort() {
        return ClockPort.systemUtc();
    }

    @Bean
    FederationAdminQuery federationAdminQuery(ClockPort clockPort, ChatProperties chatProperties) {
        return FederationFeatureFactory.adminQuery(
            clockPort,
            chatProperties.getNodeId(),
            chatProperties.getFederation().isEnabled(),
            chatProperties.getFederation().getPeerDomain()
        );
    }

    @Bean
    AdminObservabilityQuery adminObservabilityQuery(FederationAdminQuery federationAdminQuery) {
        return AdminFeatureFactory.observabilityQuery(federationAdminQuery);
    }

    @Bean
    AttachmentStoragePort attachmentStoragePort(ChatProperties chatProperties) {
        return new FilesystemAttachmentStorage(chatProperties.getStorageRoot());
    }
}
