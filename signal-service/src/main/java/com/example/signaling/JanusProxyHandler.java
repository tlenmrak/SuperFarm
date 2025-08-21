package com.example.signaling;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.web.client.RestTemplate;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class JanusProxyHandler extends TextWebSocketHandler {

    public static final String START = "/start";
    public static final String SFU_HOST = "sfuHost";
    public static final String SFU_PORT = "sfuPort";
    @Value("${janus.url}")
    private String janusUrl;

    @Value("${videobuss.url}")
    private String videoBussUrl;

    @Value("${janus.rtp.host}")
    private String rtpHost;

    @Value("${janus.rtp.port}")
    private int rtpPort;

    private final RestTemplate rest ;
    private final Map<String, WebSocketSession> janusSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession frontendSession) throws Exception {
        var client = new StandardWebSocketClient();
        var janusSession = client.doHandshake(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
                checkSessionAndSendNextMessage(frontendSession.isOpen(), frontendSession, message);
            }
        }, janusUrl).get();

        janusSessions.put(frontendSession.getId(), janusSession);
        rest.postForObject(videoBussUrl + START, Map.of(SFU_HOST, rtpHost, SFU_PORT, rtpPort), String.class);
        log.info("Streaming started for session {}", frontendSession.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession frontendSession, TextMessage message) throws Exception {
        var janusSession = janusSessions.get(frontendSession.getId());
        checkSessionAndSendNextMessage(isSessionOpen(janusSession), janusSession, message);
    }


    @Override
    public void afterConnectionClosed(WebSocketSession frontendSession, CloseStatus status) throws Exception {
        var janusSession = janusSessions.remove(frontendSession.getId());
        checkSessionAndClose(janusSession);
        log.info("Session {} closed", frontendSession.getId());
    }

    private void checkSessionAndSendNextMessage(boolean janusSession, WebSocketSession janusSession1, TextMessage message) throws IOException {
        if (janusSession) {
            janusSession1.sendMessage(message);
        }
    }
    private void checkSessionAndClose(WebSocketSession janusSession) throws IOException {
        if (isSessionOpen(janusSession)) {
            janusSession.close();
        }
    }

    private boolean isSessionOpen(WebSocketSession janusSession) {
        return janusSession != null && janusSession.isOpen();
    }
}

