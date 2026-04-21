package edu.artemiy.chat.app.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
class ChatWebSocketConfiguration implements WebSocketConfigurer {

    private final ChatWebSocketHandler chatWebSocketHandler;
    private final ChatWebSocketHandshakeInterceptor chatWebSocketHandshakeInterceptor;

    ChatWebSocketConfiguration(
        ChatWebSocketHandler chatWebSocketHandler,
        ChatWebSocketHandshakeInterceptor chatWebSocketHandshakeInterceptor
    ) {
        this.chatWebSocketHandler = chatWebSocketHandler;
        this.chatWebSocketHandshakeInterceptor = chatWebSocketHandshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatWebSocketHandler, "/ws")
            .addInterceptors(chatWebSocketHandshakeInterceptor);
    }
}
