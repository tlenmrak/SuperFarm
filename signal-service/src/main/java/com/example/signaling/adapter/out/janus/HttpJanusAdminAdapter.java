package com.example.signaling.adapter.out.janus;

import com.example.signaling.port.out.JanusAdminPort;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

import static com.example.signaling.constant.Const.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class HttpJanusAdminAdapter implements JanusAdminPort {

    private final RestTemplate rest;

    @Value("${janus.http}")
    private String janusHttpUrl;

    private static String tx() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    @Override
    public JsonNode createSession() {
        return rest.postForObject(
                janusHttpUrl,
                Map.of(JANUS, CREATE, TRANSACTION, tx()),
                JsonNode.class);
    }

    @Override
    public JsonNode attachStreaming(long sessionId) {
        return rest.postForObject(
                janusHttpUrl + "/" + sessionId,
                Map.of(JANUS, ATTACH, PLUGIN, JANUS_PLUGIN_STREAMING, TRANSACTION, tx()),
                JsonNode.class);
    }

    @Override
    public JsonNode message(long sessionId, long handleId, Map<String, Object> body) {
        return rest.postForObject(
                janusHttpUrl + "/" + sessionId + "/" + handleId,
                Map.of(JANUS, MESSAGE, BODY, body, TRANSACTION, tx()),
                JsonNode.class
        );
    }

    @Override
    public void detach(long sessionId, long handleId) {
        rest.postForObject(janusHttpUrl + "/" + sessionId + "/" + handleId,
                Map.of("janus", "detach", "transaction", tx()), JsonNode.class);
    }

    @Override
    public void destroySession(long sessionId) {
        rest.postForObject(janusHttpUrl + "/" + sessionId,
                Map.of("janus", "destroy", "transaction", tx()), JsonNode.class);
    }

    @Override
    public JsonNode longPollOnce(long sessionId) {
        var url = janusHttpUrl + "/" + sessionId + "?rid=" + System.nanoTime() + "&maxev=10";
        try {
            return rest.getForObject(url, JsonNode.class);
        } catch (Exception e) {
            log.debug("Long-poll failed: {}", e.getMessage());
            return null;
        }
    }
}
