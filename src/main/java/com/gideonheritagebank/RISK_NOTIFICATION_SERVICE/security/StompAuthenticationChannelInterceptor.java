package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.security;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.security.Principal;
import java.util.Set;
import java.util.UUID;

@Component
public class StompAuthenticationChannelInterceptor
        implements ChannelInterceptor {

    private static final Set<String> ALLOWED_SUBSCRIPTIONS = Set.of(
            "/user/queue/notifications",
            "/user/queue/notifications/unread-count"
    );

    @Override
    public Message<?> preSend(
            Message<?> message,
            MessageChannel channel
    ) {

        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                message,
                StompHeaderAccessor.class
        );

        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();

        switch (command) {

            case CONNECT, STOMP -> authenticateConnection(accessor);

            case SUBSCRIBE -> authorizeSubscription(accessor);

            case SEND -> rejectClientSend();

            default -> {
                // DISCONNECT, UNSUBSCRIBE and heartbeat-related
                // processing do not need a separate destination rule.
            }
        }

        return message;
    }

    private void authenticateConnection(
            StompHeaderAccessor accessor
    ) {
        Authentication authentication = requireAuthenticatedJwt(accessor);

        /*
         * The Principal comes from the authenticated HTTP WebSocket
         * handshake. Spring Security has already validated the JWT
         * signature, issuer, audience and expiry.
         *
         * We validate the subject again here because it is the identity
         * used by our user-specific notification destinations.
         */
        UUID userId = extractUserId(authentication);

        /*
         * Preserve the verified Authentication as the STOMP session user.
         * Never replace it with a user ID or role supplied by the client.
         */
        accessor.setUser(authentication);
    }

    private void authorizeSubscription(
            StompHeaderAccessor accessor
    ) {
        Authentication authentication = requireAuthenticatedJwt(accessor);

        extractUserId(authentication);

        String destination = accessor.getDestination();

        if (destination == null || !ALLOWED_SUBSCRIPTIONS.contains(destination)) {

            throw new MessageDeliveryException(
                    "Subscription to this destination is not permitted."
            );
        }
    }

    private Authentication requireAuthenticatedJwt(
            StompHeaderAccessor accessor
    ) {
        Principal principal = accessor.getUser();

        if (!(principal instanceof JwtAuthenticationToken jwtAuthentication)
                || !jwtAuthentication.isAuthenticated()) {

            throw new MessageDeliveryException(
                    "An authenticated JWT is required for this WebSocket operation."
            );
        }

        Instant expiresAt = jwtAuthentication.getToken().getExpiresAt();

        if (expiresAt == null || !expiresAt.isAfter(Instant.now())) {

            throw new MessageDeliveryException(
                    "The WebSocket access token has expired. Reconnect after refreshing your session."
            );
        }

        return jwtAuthentication;
    }

    private UUID extractUserId(Authentication authentication) {

        JwtAuthenticationToken jwtAuthentication = (JwtAuthenticationToken) authentication;

        String subject = jwtAuthentication.getToken().getSubject();

        if (subject == null || subject.isBlank()) {

            throw new MessageDeliveryException("The authenticated user identifier is missing.");
        }

        try {
            return UUID.fromString(subject);

        } catch (IllegalArgumentException exception) {

            throw new MessageDeliveryException(
                    "The authenticated user identifier is invalid."
            );
        }
    }

    private void rejectClientSend() {

        /*
         * The client is currently allowed to receive notifications only.
         * Notifications are created by trusted backend event processing.
         */
        throw new MessageDeliveryException(
                "Client messages are not permitted on this WebSocket endpoint."
        );
    }
}
