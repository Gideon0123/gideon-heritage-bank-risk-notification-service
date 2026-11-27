package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.config;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.security.StompAuthenticationChannelInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class NotificationWebSocketConfiguration
        implements WebSocketMessageBrokerConfigurer {

    private final StompAuthenticationChannelInterceptor authenticationInterceptor;

    @Value("${notification.websocket.allowed-origin-patterns:http://localhost:3000,http://localhost:5173}")
    private String[] allowedOriginPatterns;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {

        registry.addEndpoint("/ws/notifications")
                .setAllowedOriginPatterns(allowedOriginPatterns);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {

        // Destinations used by the server to publish notifications.
        registry.enableSimpleBroker("/queue");

        // Destinations used for client-to-server application messages.
        // We are not exposing client SEND operations yet.
        registry.setApplicationDestinationPrefixes("/app");

        // Enables user-specific destinations.
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(
            ChannelRegistration registration
    ) {

        registration.interceptors(authenticationInterceptor);
    }
}