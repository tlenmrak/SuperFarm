package com.example.signaling.port.out;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

public interface JanusAdminPort {
    JsonNode createSession();
    JsonNode attachStreaming(long sessionId);
    JsonNode message(long sessionId, long handleId, Map<String, Object> body);
    void detach(long sessionId, long handleId);
    void destroySession(long sessionId);
    JsonNode longPollOnce(long sessionId);
}
