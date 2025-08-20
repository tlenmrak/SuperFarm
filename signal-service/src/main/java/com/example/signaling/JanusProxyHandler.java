package com.example.signaling;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import org.springframework.web.client.RestTemplate;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class JanusProxyHandler extends TextWebSocketHandler {

    private final String janusUrl;
    private final String videoBussUrl;
    private final String rtpHost;
    private final int rtpPort;
    private final Map<String, WebSocketSession> janusSessions = new ConcurrentHashMap<>();
    private final RestTemplate rest = new RestTemplate();

    public JanusProxyHandler(
            @Value("${janus.url}") String janusUrl,
            @Value("${videobuss.url}") String videoBussUrl,
            @Value("${janus.rtp.host}") String rtpHost,
            @Value("${janus.rtp.port}") int rtpPort) {
        this.janusUrl = janusUrl;
        this.videoBussUrl = videoBussUrl;
        this.rtpHost = rtpHost;
        this.rtpPort = rtpPort;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession frontendSession) throws Exception {
        StandardWebSocketClient client = new StandardWebSocketClient();
        WebSocketSession janusSession = client.doHandshake(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
                if (frontendSession.isOpen()) {
                    frontendSession.sendMessage(message);
                }
            }
        }, janusUrl).get();

        janusSessions.put(frontendSession.getId(), janusSession);
        rest.postForObject(
            videoBussUrl + "/start",
            Map.of("sfuHost", rtpHost, "sfuPort", rtpPort),
            String.class);
    }

    @Override
    protected void handleTextMessage(WebSocketSession frontendSession, TextMessage message) throws Exception {
        WebSocketSession janusSession = janusSessions.get(frontendSession.getId());
        if (janusSession != null && janusSession.isOpen()) {
            janusSession.sendMessage(message);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession frontendSession, CloseStatus status) throws Exception {
        WebSocketSession janusSession = janusSessions.remove(frontendSession.getId());
        if (janusSession != null && janusSession.isOpen()) {
            janusSession.close();
        }
    }
}
