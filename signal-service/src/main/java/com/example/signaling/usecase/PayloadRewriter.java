package com.example.signaling.usecase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static com.example.signaling.constant.Const.BODY;
import static com.example.signaling.constant.Const.ID;
import static com.example.signaling.constant.Const.PIN;
import static com.example.signaling.constant.Const.REQUEST;

@Component
@RequiredArgsConstructor
@Slf4j
public class PayloadRewriter {
    public static final String WATCH = "watch";
    private final ObjectMapper mapper;

    public String rewriteWatch(String payload, long mountpointId, String pin) {
        try {
            var root = mapper.readTree(payload);
            var body = root.path(BODY);
            if (isWatch(body)) {
                setPinAndMountpointId(mountpointId, pin, (ObjectNode) body);
                payload = mapper.writeValueAsString(root);
                log.debug("Rewrote watch: id={} pin={}", mountpointId, pin);
            }
        } catch (Exception e) {
            log.debug("Failed to rewrite message", e);
        }
        return payload;
    }

    private void setPinAndMountpointId(long mountpointId, String pin, ObjectNode body) {
        body.put(ID, mountpointId);
        if (pin != null) {
            body.put(PIN, pin);
        }
    }

    private boolean isWatch(JsonNode body) {
        return body.isObject() && WATCH.equals(body.path(REQUEST).asText());
    }
}
