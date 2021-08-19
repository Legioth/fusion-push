package com.example.application.framework;

import java.util.HashMap;
import java.util.Map;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
@EnableWebSocket
public class PushConfigurer implements WebSocketConfigurer {
    private Map<String, WebSocketHandler> endpoints = new HashMap<>();

    public PushConfigurer(ApplicationContext context, ObjectMapper objectMapper) {
        String[] endpointNames = context.getBeanNamesForAnnotation(PushEndpoint.class);
        for (String name : endpointNames) {
            PushEndpointHandler handler = new PushEndpointHandler(name, context.getBean(name), objectMapper);
            handler.logCode();
            endpoints.put(name, handler);
        }
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        endpoints.forEach((name, handler) -> registry.addHandler(handler, "/" + name));
    }
}
