package com.example.signaling.adapter.in.ws;

import com.example.signaling.port.out.JanusWsPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.SubProtocolCapable;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class JanusWsAdapter implements JanusWsPort {

    public static final int TIMEOUT = 30;
    @Value("${janus.url}")
    private String janusUrl;
    private final StandardWebSocketClient wsClient;

    // frontendId -> future Janus WS session
    private final ConcurrentMap<String, CompletableFuture<WebSocketSession>> futures = new ConcurrentHashMap<>();

    @Override
    public CompletableFuture<Void> connectIfNeeded(String fid, WebSocketSession frontend) {
        return futures
                .computeIfAbsent(fid, key -> getWebSocketSessionFuture(fid, frontend))
                .thenApply(s -> null);
    }

    @Override
    public void send(String fid, String payload) throws Exception {
        var s = futures.get(fid);
        if (s == null)
        {
            throw new IllegalStateException("Janus WS not connected for fid=" + fid);
        }
        var ws = s.get(30, TimeUnit.SECONDS);
        ws.sendMessage(new TextMessage(payload));
    }

    @Override
    public void close(String fid) {
        var future = futures.remove(fid);
        if (future != null) {
            future.whenComplete((success, ex) -> forceCloseConnection(success));
            future.cancel(true);
        }
    }

    private CompletableFuture<WebSocketSession> getWebSocketSessionFuture(String fid, WebSocketSession frontend) {
        return CompletableFuture
                .supplyAsync(() -> connectWithRetry(frontend))
                .whenComplete((success, error) -> removeFutureIfError(fid, error));
    }

    private WebSocketSession connectWithRetry(WebSocketSession frontendSession) {
        final var maxAttempts = 10;
        final var headers = new WebSocketHttpHeaders();
        headers.add("Origin", "http://localhost");
        headers.setSecWebSocketProtocol(List.of("janus-protocol"));

        class Handler extends TextWebSocketHandler implements SubProtocolCapable {
            @Override
            public List<String> getSubProtocols() {
                return List.of("janus-protocol");
            }

            @Override
            public void afterConnectionEstablished(WebSocketSession s) {
                log.info("Janus WS connected: uri={} proto={}", s.getUri(), s.getAcceptedProtocol());
            }

            @Override
            protected void handleTextMessage(WebSocketSession s, TextMessage msg) throws Exception {
                if (frontendSession.isOpen()) frontendSession.sendMessage(msg);
            }

            @Override
            public void handleTransportError(WebSocketSession s, Throwable ex) {
                log.error("Janus WS transport error", ex);
                try {
                    if (frontendSession.isOpen()) frontendSession.close(CloseStatus.SERVER_ERROR);
                } catch (Exception ignore) {
                }
            }

            @Override
            public void afterConnectionClosed(WebSocketSession s, CloseStatus st) {
                log.info("Janus WS closed: code={} reason={}", st.getCode(), st.getReason());
            }
        }

        for (var attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return wsClient
                        .doHandshake(new Handler(), headers, URI.create(janusUrl))
                        .get(TIMEOUT, TimeUnit.SECONDS);
            } catch (Exception e) {
                var backoffMs = 500L * attempt;
                log.warn("Janus WS not ready (try {}/{}): {} — retry in {} ms",
                        attempt, maxAttempts, e.getMessage(), backoffMs);
                if (attempt == maxAttempts) {
                    throw new RuntimeException(e);
                }
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(ie);
                }
            }
        }
        throw new IllegalStateException("Unreachable");
    }
    private void removeFutureIfError(String fid, Throwable error) {
        if (error != null) {
            futures.remove(fid);
        }
    }

    private void forceCloseConnection(WebSocketSession success) {
        if (success != null && success.isOpen()) {
            try {
                success.close();
            } catch (Exception ignore) {
            }
        }
    }
}
