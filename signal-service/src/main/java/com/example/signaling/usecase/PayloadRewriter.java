package com.example.signaling.usecase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PayloadRewriter {
    private final ObjectMapper mapper;

    public String rewriteWatch(String payload, long mountpointId, String pin) {
        try {
            var root = mapper.readTree(payload);
            var body = root.path("body");
            if (body.isObject() && "watch".equals(body.path("request").asText())) {
                ((ObjectNode) body).put("id", mountpointId);
                if (pin != null) ((ObjectNode) body).put("pin", pin);
                payload = mapper.writeValueAsString(root);
                log.debug("Rewrote watch: id={} pin={}", mountpointId, pin);
            }
        } catch (Exception e) {
            log.debug("Failed to rewrite message", e);
        }
        return payload;
    }
}
