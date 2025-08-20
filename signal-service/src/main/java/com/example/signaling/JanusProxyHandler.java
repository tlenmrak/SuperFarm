package com.example.signaling;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class JanusProxyHandler extends TextWebSocketHandler {

    private final String janusUrl;
    private WebSocketSession janusSession;

    public JanusProxyHandler(@Value("${janus.url}") String janusUrl) {
        this.janusUrl = janusUrl;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession frontendSession) throws Exception {
        StandardWebSocketClient client = new StandardWebSocketClient();
        this.janusSession = client.doHandshake(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
                if (frontendSession.isOpen()) {
                    frontendSession.sendMessage(message);
                }
            }
        }, janusUrl).get();
    }

    @Override
    protected void handleTextMessage(WebSocketSession frontendSession, TextMessage message) throws Exception {
        if (janusSession != null && janusSession.isOpen()) {
            janusSession.sendMessage(message);
        }
    }
}
