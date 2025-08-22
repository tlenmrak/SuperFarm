package com.example.signaling.port.out;

import org.springframework.web.socket.WebSocketSession;

import java.util.concurrent.CompletableFuture;

public interface JanusWsPort {
    CompletableFuture<Void> connectIfNeeded(String frontendId, WebSocketSession frontendSession);
    void send(String frontendId, String payload) throws Exception;
    void close(String frontendId);
}
