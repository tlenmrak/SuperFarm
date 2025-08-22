package com.example.signaling.adapter.in.ws;

import com.example.signaling.port.in.CloseConnectionUseCase;
import com.example.signaling.port.in.ForwardMessageUseCase;
import com.example.signaling.port.in.OpenConnectionUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.SubProtocolCapable;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class JanusProxyHandler extends TextWebSocketHandler implements SubProtocolCapable {

    private final OpenConnectionUseCase openConnectionUseCase;
    private final ForwardMessageUseCase forwardMessageUseCase;
    private final CloseConnectionUseCase closeConnectionUseCase;

    @Override
    public List<String> getSubProtocols() {
        return List.of("janus-protocol");
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession frontend) {
        final var fid = frontend.getId();
        openConnectionUseCase.open(fid) // создаём mountpoint заранее (асинхронно)
                .whenComplete((mp, ex) -> {
                    if (ex != null) {
                        log.error("Failed to prepare mountpoint for fid={}", fid, ex);
                        safeClose(frontend, CloseStatus.SERVER_ERROR);
                    } else {
                        log.info("Mountpoint {} ready at {}:{}", mp.mountpointId(), mp.host(), mp.port());
                    }
                });
    }

    @Override
    protected void handleTextMessage(WebSocketSession frontend, TextMessage msg) throws Exception {
        forwardMessageUseCase.forward(frontend, msg.getPayload()); // use case сам обеспечит коннект к Janus и форвардинг
    }

    @Override
    public void afterConnectionClosed(WebSocketSession frontend, CloseStatus status) {
        closeConnectionUseCase.close(frontend.getId());
    }

    private static void safeClose(WebSocketSession s, CloseStatus status) {
        try { if (s.isOpen()) s.close(status); } catch (Exception ignore) {}
    }
}
