package com.example.signaling.usecase;

import com.example.signaling.model.dto.Mountpoint;
import com.example.signaling.port.out.JanusAdminPort;
import com.example.signaling.port.in.OpenConnectionUseCase;
import com.example.signaling.port.core.SessionStatePort;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static com.example.signaling.constant.Const.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class OpenConnectionInteractor implements OpenConnectionUseCase {


    public static final String KEEPALIVE = "keepalive";
    public static final String EVENT = "event";
    public static final String JANUS_PLUGIN_STREAMING = "janus.plugin.streaming";
    private final JanusAdminPort janus;
    private final SessionStatePort state;

    @Value("${janus.adminKey}")
    private String adminKey;

    @Value("${janus.rtpHost}")
    private String rtpHost;

    private static final SecureRandom SR = new SecureRandom();

    @Override
    public CompletableFuture<Mountpoint> open(String frontendId) {
        // Создание mountpoint — асинхронно, кэшируется в state
        return state.getOrInitMountpoint(frontendId, () ->
                CompletableFuture.supplyAsync(this::createMountpointFlow)
        );
    }

    /* ====== создание mountpoint (session -> attach -> create -> wait created) ====== */

    private Mountpoint createMountpointFlow() {
        var sessionInfo = janus.createSession();
        var sessionId = sessionInfo.path(DATA).path(ID).asLong();

        var attachInfo = janus.attachStreaming(sessionId);
        var handleId = attachInfo.path(DATA).path(ID).asLong();

        var pin = sixDigitPin();
        var body = mountpointCreateBody(sessionId, pin);
        var postRes = janus.message(sessionId, handleId, body);

        var janusType = postRes.path(JANUS).asText();
        log.debug("create/message response: {}", postRes);

        var streamData = extractJanusStreamDataOrWait(sessionId, handleId, postRes, janusType);
        var mountpointId = streamData.path(STREAM).path(ID).asLong();

        var videoPort = extractVideoPort(streamData);
        if (videoPort == null) {
            throw new IllegalStateException("Janus created stream but no video port: " + streamData);
        }
        log.info("Created mountpoint {} on {}:{}", mountpointId, rtpHost, videoPort);

        return new Mountpoint(sessionId, handleId, mountpointId, rtpHost, videoPort, pin);
    }

    private JsonNode extractJanusStreamDataOrWait(long sessionId, long handleId, JsonNode postRes, String janusType) {
        // сначала пробуем извлечь из немедленного ответа Janus
        var streamData = switch (janusType) {
            case ERROR   -> handleCreateError(postRes);
            case SUCCESS -> extractStreamDataFromSuccess(postRes);    // вернёт stream или null
            case ACK     -> null;                                     // ждём событие через long-poll
            default      -> handleUnexpectedJanusType(janusType);
        };

        // если сразу не получили stream — ждём событие "created"
        return (streamData != null)
                ? streamData
                : waitStreamingCreated(sessionId, handleId, 20_000);
    }

    private JsonNode handleCreateError(JsonNode postRes) {
        var err = postRes.path(ERROR).path(REASON).asText(postRes.toString());
        throw new IllegalStateException("Janus returned error on create: " + err);
    }

    // Ветвь SUCCESS: пытаемся достать plugindata.data.stream
    private JsonNode extractStreamDataFromSuccess(JsonNode postRes) {
        var pluginData = postRes.path(PLUGINDATA);
        if (!pluginData.isObject()) return null;
        var data = pluginData.path(DATA);
        return data.has(STREAM) ? data : null;
    }

    // Ветвь default: неожиданный janusType
    private JsonNode handleUnexpectedJanusType(String janusType) {
        throw new IllegalStateException("Unexpected Janus reply on create: " + janusType);
    }

    private JsonNode waitStreamingCreated(long sessionId, long handleId, int timeoutMs) {
        var deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            var janusResponse = janus.longPollOnce(sessionId);
            if (janusResponse == null) {
                continue;
            }

            var jt = janusResponse.path(JANUS).asText();
            log.debug("LP event: {}", janusResponse);

            if (KEEPALIVE.equals(jt) || TIMEOUT.equals(jt)) {
                continue;
            }
            if (!EVENT.equals(jt)) {
                continue;
            }

            var sender = janusResponse.path(SENDER).asLong();
            if (sender != handleId) {
                continue;
            }

            var pd = janusResponse.path(PLUGINDATA);
            if (!JANUS_PLUGIN_STREAMING.equals(pd.path(PLUGIN).asText())) {
                continue;
            }

            var data = pd.path(DATA);
            if (data.has(ERROR)) {
                throw new IllegalStateException("Janus streaming error: " + data.path(ERROR).asText());
            }
            if (data.has(STREAM)) {
                return data;
            }
        }
        throw new IllegalStateException("Timed out waiting for Streaming 'created' event");
    }

    private Integer extractVideoPort(JsonNode streamData) {
        for (var p : streamData.path(STREAM).path(PORTS)) {
            if (VIDEO.equals(p.path(TYPE).asText())) {
                return p.path(PORT).asInt();
            }
        }
        return null;
    }

    private Map<String, Object> mountpointCreateBody(long sessionId, String pin) {
        var video = new LinkedHashMap<String, Object>();
        video.put(TYPE, VIDEO);
        video.put(MID, MID_VALUE);
        video.put(PORT, NULL_VALUE_INT);
        video.put(PT, PT_VALUE);
        video.put(CODEC, CODEC_VALUE);
        video.put(RTPMAP, RTPMAP_VALUE);
        video.put(FMTP, FMTP_VALUE);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put(REQUEST, CREATE);
        body.put(ADMIN_KEY, adminKey);
        body.put(TYPE, RTP);
        body.put(DESCRIPTION, DESCRIPTION_VALUE + sessionId);
        body.put(IS_PRIVATE, true);
        body.put(PIN, pin);
        body.put(PERMANENT, false);
        body.put(MEDIA, List.of(video));
        return body;
    }

    private static String sixDigitPin() {
        return String.format(FORMAT_PIN, SR.nextInt(1_000_000));
    }

}
