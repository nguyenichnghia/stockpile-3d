package com.stockpile.realtime.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over WebSocket for pushing placement deltas to the 3D scene. Clients
 * connect to {@code /ws} and subscribe to {@code /topic/lane/{laneId}} to receive
 * only the region they view (see ADR-0005).
 *
 * <p>The STOMP handshake needs its own allowed-origins — {@code CorsConfig} only
 * covers {@code /api/**} — so we reuse the same {@code app.cors.allowed-origins}
 * property here.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

	@Value("${app.cors.allowed-origins:http://localhost:3000}")
	private String[] allowedOrigins;

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		// Native WebSocket (no SockJS); @stomp/stompjs on the client uses raw WS.
		registry.addEndpoint("/ws").setAllowedOrigins(allowedOrigins);
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		registry.enableSimpleBroker("/topic");            // in-memory broker -> /topic/lane/{laneId}
		registry.setApplicationDestinationPrefixes("/app"); // client->server (unused in v1)
	}
}
