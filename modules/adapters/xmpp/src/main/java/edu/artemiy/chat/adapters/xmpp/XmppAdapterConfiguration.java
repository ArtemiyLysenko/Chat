package edu.artemiy.chat.adapters.xmpp;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(XmppProperties.class)
class XmppAdapterConfiguration {
}
