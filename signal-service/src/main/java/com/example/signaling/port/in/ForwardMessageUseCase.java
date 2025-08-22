package com.example.signaling.port.in;

import org.springframework.web.socket.WebSocketSession;

public interface ForwardMessageUseCase {
    void forward(WebSocketSession frontendSession, String payload) throws Exception;
}
