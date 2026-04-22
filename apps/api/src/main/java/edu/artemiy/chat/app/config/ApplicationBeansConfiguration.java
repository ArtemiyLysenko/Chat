package edu.artemiy.chat.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import edu.artemiy.chat.adapters.storage.filesystem.FilesystemAttachmentStorage;
import edu.artemiy.chat.admin.api.AdminFeatureFactory;
import edu.artemiy.chat.admin.api.AdminObservabilityQuery;
import edu.artemiy.chat.attachments.spi.AttachmentStoragePort;
import edu.artemiy.chat.core.kernel.ClockPort;
import edu.artemiy.chat.federation.api.FederationAdminQuery;
import edu.artemiy.chat.federation.api.FederationSettings;
import edu.artemiy.chat.identity.api.IdentitySettings;

@Configuration
class ApplicationBeansConfiguration {

    @Bean
    ClockPort clockPort() {
        return ClockPort.systemUtc();
    }

    @Bean
    IdentitySettings identitySettings(ChatProperties chatProperties) {
        return new IdentitySettings(
            chatProperties.getAuth().getSessionTtl(),
            chatProperties.getAuth().getPasswordResetTtl()
        );
    }

    @Bean
    FederationSettings federationSettings(ChatProperties chatProperties) {
        return new FederationSettings(chatProperties.getNodeId());
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
