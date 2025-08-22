//package com.example.signaling.config;
//
//import java.util.LinkedHashMap;
//import java.util.List;
//import java.util.Map;
//import java.util.UUID;
//import java.util.concurrent.ConcurrentHashMap;
//
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//
//import org.springframework.web.client.RestTemplate;
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.fasterxml.jackson.databind.node.ObjectNode;
//
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Component;
//import org.springframework.web.socket.CloseStatus;
//import org.springframework.web.socket.TextMessage;
//import org.springframework.web.socket.WebSocketHttpHeaders;
//import org.springframework.web.socket.WebSocketSession;
//import org.springframework.web.socket.SubProtocolCapable;
//import org.springframework.web.socket.client.standard.StandardWebSocketClient;
//import org.springframework.web.socket.handler.TextWebSocketHandler;
//
//import jakarta.annotation.PostConstruct;
//
//@Component
//@RequiredArgsConstructor
//@Slf4j
//public class JanusProxyHandler extends TextWebSocketHandler implements SubProtocolCapable {
//
//    public static final String DATA = "data";
//    public static final String ID = "id";
//    public static final String PLUGINDATA = "plugindata";
//    public static final String ERROR = "error";
//    public static final String SUCCESS = "success";
//    public static final String REASON = "reason";
//    public static final String ACK = "ack";
//
//    @Value("${janus.url}")
//    private String janusUrl;
//
//    @Value("${janus.adminKey}")
//    private String adminKey;
//
//    @Value("${janus.rtpHost}")
//    private String rtpHost;
//
//    @Value("${janus.http}")
//    private String janusHttpUrl;
//
//    @Value("${videobuss.url}")
//    private String videoBussUrl;
//
//    private final Map<String, WebSocketSession> janusSessions = new ConcurrentHashMap<>();
//    private final Map<String, MountpointInfo> mountpoints = new ConcurrentHashMap<>();
//    private final RestTemplate rest;
//    private final ObjectMapper mapper;
//
//    @PostConstruct
//    public void wsTweaks() {
//        // не пытаться ходить через системные прокси на localhost
//        System.setProperty("java.net.useSystemProxies", "false");
//        System.setProperty("http.proxyHost", "");
//        System.setProperty("https.proxyHost", "");
//        System.setProperty("socksProxyHost", "");
//        // отключить расширения (permessage-deflate) у Tomcat WebSocket-клиента
//        System.setProperty("org.apache.tomcat.websocket.DISABLE_BUILTIN_EXTENSIONS", "true");
//    }
//
//    /* ========================== WebSocket lifecycle ========================== */
//
//    @Override
//    public List<String> getSubProtocols() {
//        // Браузер (janus.js) придёт с "janus-protocol"
//        return List.of("janus-protocol");
//    }
//
//    @Override
//    public void afterConnectionEstablished(WebSocketSession frontendSession) throws Exception {
//        // Создаём mountpoint заранее (получаем host/port), но WS к Janus пока НЕ открываем.
//        MountpointInfo info = createMountpoint();
//        mountpoints.put(frontendSession.getId(), info);
//        log.info("Mountpoint {} ready at {}:{}", info.mountpointId, info.host, info.port);
//        // Дальше ждём первое сообщение от фронта в handleTextMessage(...)
//    }
//
//    @Override
//    protected void handleTextMessage(WebSocketSession frontendSession, TextMessage message) throws Exception {
//        final String fid = frontendSession.getId();
//
//        // ленивое подключение к Janus при первом сообщении от фронта
//        var janusSession = janusSessions.get(fid);
//        if (janusSession == null || !janusSession.isOpen()) {
//            synchronized (janusSessions) {
//                janusSession = janusSessions.get(fid);
//                if (janusSession == null || !janusSession.isOpen()) {
//                    janusSession = connectJanusWithRetry(frontendSession);
//                    janusSessions.put(fid, janusSession);
//
//                    // как только труба к Janus готова — запускаем видеострим в Janus
//                    var info = mountpoints.get(fid);
//                    if (info != null && !info.started) {
//                        sendStartCommandToVideoBuss(info);
//                        info.started = true;
//                        log.info("Requested VideoBuss to stream to {}:{}", info.host, info.port);
//                    }
//                }
//            }
//        }
//
//        // переписываем watch.id/pin при необходимости
//        var payload = message.getPayload();
//        var info = mountpoints.get(fid);
//        if (info != null) {
//            payload = rewriteWatchByMountinePoint(payload, info);
//        }
//
//        // форвард в Janus
//        janusSession.sendMessage(new TextMessage(payload));
//    }
//
//    @Override
//    public void afterConnectionClosed(WebSocketSession frontendSession, CloseStatus status) throws Exception {
//        var janusSession = janusSessions.remove(frontendSession.getId());
//        if (janusSession != null && janusSession.isOpen()) {
//            try { janusSession.close(); } catch (Exception ignore) {}
//        }
//        var info = mountpoints.remove(frontendSession.getId());
//        destroyMountpoint(info);
//        log.info("Session {} closed", frontendSession.getId());
//    }
//
//     private String rewriteWatchByMountinePoint(String payload, MountpointInfo info) {
//        try {
//            var root = mapper.readTree(payload);
//            var body = root.path("body");
//            if (body.isObject() && "watch".equals(body.path("request").asText())) {
//                ((ObjectNode) body).put("id", info.mountpointId);
//                if (info.pin != null) ((ObjectNode) body).put("pin", info.pin);
//                payload = mapper.writeValueAsString(root);
//                log.debug("Rewrote watch: id={} pin={}", info.mountpointId, info.pin);
//            }
//        } catch (Exception e) {
//            log.debug("Failed to rewrite message", e);
//        }
//        return payload;
//    }
//
//    private WebSocketSession connectJanusWithRetry(WebSocketSession frontendSession) throws Exception {
//        var client = new StandardWebSocketClient();
//
//        class JanusWsHandler extends TextWebSocketHandler implements SubProtocolCapable {
//            @Override public List<String> getSubProtocols() { return List.of("janus-protocol"); }
//            @Override public void afterConnectionEstablished(WebSocketSession s) {
//                log.info("Janus WS connected: uri={} proto={}", s.getUri(), s.getAcceptedProtocol());
//            }
//            @Override protected void handleTextMessage(WebSocketSession s, TextMessage msg) throws Exception {
//                if (frontendSession.isOpen()) frontendSession.sendMessage(msg);
//            }
//            @Override public void handleTransportError(WebSocketSession s, Throwable ex) {
//                log.error("Janus WS transport error", ex);
//                try { frontendSession.close(CloseStatus.SERVER_ERROR); } catch (Exception ignore) {}
//            }
//            @Override public void afterConnectionClosed(WebSocketSession s, CloseStatus st) {
//                log.info("Janus WS closed: code={} reason={}", st.getCode(), st.getReason());
//            }
//        }
//
//        var headers = new WebSocketHttpHeaders();
//        headers.add("Origin", "http://localhost");
//        // дублируем на всякий случай (иногда Tomcat читает именно отсюда), не работало без этой хуйни
//        headers.setSecWebSocketProtocol(java.util.Collections.singletonList("janus-protocol"));
//
//        final var maxAttempts = 10;
//        // на всякий случай тут пытаюсь ждать и ретраить, тож можно убрать, вроде работает
//        for (var attempt = 1; attempt <= maxAttempts; attempt++) {
//            try {
//                return client
//                        .doHandshake(new JanusWsHandler(), headers, java.net.URI.create(janusUrl)) // ws://127.0.0.1:8188/janus
//                        .get();
//            } catch (Exception e) {
//                var backoffMs = 500L * attempt;
//                log.warn("Janus WS not ready (try {}/{}): {} — retry in {} ms",
//                        attempt, maxAttempts, e.getMessage(), backoffMs);
//                if (attempt == maxAttempts) throw e;
//                Thread.sleep(backoffMs); // костыль, можно убрать но пока пусть будет
//            }
//        }
//        throw new IllegalStateException("Unreachable");
//    }
//
//    /* ============================ Mountpoint admin =========================== */
//
//    private MountpointInfo createMountpoint() {
//        // 1) session
//        var sessionInfo = sendToSFUCreateSession();
//        var sessionId = sessionInfo.path(DATA).path(ID).asLong();
//
//        // 2) attach streaming
//        var attachInfo = sendToSFUAttachVideoPlugin(sessionId);
//        var handleId = attachInfo.path(DATA).path(ID).asLong();
//
//        // 3) create
//        var pin = sixDigitPin();
//        var createBody = mountpointCreateBody(sessionId, pin);
//        var postRes = sendToSFUCreateMountainPoint(sessionId, handleId, createBody);
//
//        var janusType = postRes.path("janus").asText();
//        log.debug("create/message response: {}", postRes);
//
//        JsonNode streamData = null;
//        streamData = getStreamDataOrThrowError(sessionId, handleId, postRes, janusType, streamData);
//        var mpId = streamData.path("stream").path("id").asLong();
//
//        Integer videoPort = null;
//        videoPort = getVideoPort(streamData, videoPort);
//        if (videoPort == null) {
//            throw new IllegalStateException("Janus created stream but didn't return video port: " + streamData);
//        }
//        log.info("Created mountpoint {} on {}:{}", mpId, rtpHost, videoPort);
//
//        return new MountpointInfo(sessionId, handleId, mpId, rtpHost, videoPort, pin);
//    }
//
//    private Integer getVideoPort(JsonNode streamData, Integer videoPort) {
//        for (var p : streamData.path("stream").path("ports")) {
//            if ("video".equals(p.path("type").asText())) {
//                videoPort = p.path("port").asInt();
//                break;
//            }
//        }
//        return videoPort;
//    }
//
//    private JsonNode getStreamDataOrThrowError(long sessionId, long handleId, JsonNode postRes, String janusType, JsonNode streamData) {
//        if (ERROR.equals(janusType)) {
//            throwError(postRes);
//        } else if (SUCCESS.equals(janusType)) {
//            streamData = getStreamData(postRes, streamData);
//        } else if (!ACK.equals(janusType)) {
//            throw new IllegalStateException("Unexpected Janus reply on create: " + janusType);
//        }
//
//        if (streamData == null) {
//            streamData = waitStreamingCreated(sessionId, handleId, 20_000);
//        }
//        return streamData;
//    }
//
//    private JsonNode getStreamData(JsonNode postRes, JsonNode streamData) {
//        var pd = postRes.path(PLUGINDATA);
//        if (pd.isObject()) {
//            var d = pd.path(DATA);
//            if (d.has("stream")) {
//                streamData = d;
//            }
//        }
//        return streamData;
//    }
//
//    private void throwError(JsonNode postRes) {
//        var err = postRes.path(ERROR).path(REASON).asText(postRes.toString());
//        throw new IllegalStateException("Janus returned error on create: " + err);
//    }
//
//    private void destroyMountpoint(MountpointInfo info) {
//        if (info == null) return;
//        try {
//            Map<String, Object> body = Map.of(
//                    "request", "destroy",
//                    "id", info.mountpointId,
//                    "admin_key", adminKey,
//                    "permanent", false
//            );
//            rest.postForObject(janusHttpUrl + "/" + info.sessionId + "/" + info.handleId, janusMessage(body), JsonNode.class);
//            rest.postForObject(janusHttpUrl + "/" + info.sessionId + "/" + info.handleId, Map.of("janus", "detach", "transaction", tx()), JsonNode.class);
//            rest.postForObject(janusHttpUrl + "/" + info.sessionId, Map.of("janus", "destroy", "transaction", tx()), JsonNode.class);
//            log.info("Destroyed mountpoint {}", info.mountpointId);
//        } catch (Exception e) {
//            log.warn("Failed to destroy mountpoint {}", info.mountpointId, e);
//        }
//    }
//
//    /* =============================== HTTP calls ============================== */
//
//    private JsonNode sendToSFUCreateMountainPoint(long sessionId, long handleId, Map<String, Object> body) {
//        return rest.postForObject(
//                janusHttpUrl + "/" + sessionId + "/" + handleId,
//                janusMessage(body),
//                JsonNode.class
//        );
//    }
//
//    private JsonNode sendToSFUAttachVideoPlugin(long sessionId) {
//        return rest.postForObject(
//                janusHttpUrl + "/" + sessionId,
//                mountpointCreateCommand(),
//                JsonNode.class);
//    }
//
//    private JsonNode sendToSFUCreateSession() {
//        return rest.postForObject(
//                janusHttpUrl,
//                Map.of("janus", "create", "transaction", tx()),
//                JsonNode.class);
//    }
//
//    private void sendStartCommandToVideoBuss(MountpointInfo info) {
//        rest.postForObject(videoBussUrl + "/start", Map.of("sfuHost", info.host, "sfuPort", info.port), String.class);
//    }
//
//    /* ============================ Long-poll helpers ========================== */
//
//    private JsonNode longPollOnce(long sessionId) {
//        var url = janusHttpUrl + "/" + sessionId + "?rid=" + System.nanoTime() + "&maxev=10";
//        try {
//            return rest.getForObject(url, JsonNode.class);
//        } catch (Exception e) {
//            log.debug("Long-poll failed: {}", e.getMessage());
//            return null;
//        }
//    }
//
//    private JsonNode waitStreamingCreated(long sessionId, long handleId, int timeoutMs) {
//        var deadline = System.currentTimeMillis() + timeoutMs;
//        while (System.currentTimeMillis() < deadline) {
//            var ev = longPollOnce(sessionId);
//            if (ev == null) continue;
//
//            var jt = ev.path("janus").asText();
//            log.debug("LP event: {}", ev);
//
//            if ("keepalive".equals(jt) || "timeout".equals(jt)) continue;
//            if (!"event".equals(jt)) continue;
//
//            var sender = ev.path("sender").asLong();
//            if (sender != handleId) continue;
//
//            var pd = ev.path(PLUGINDATA);
//            if (!"janus.plugin.streaming".equals(pd.path("plugin").asText())) continue;
//
//            var data = pd.path(DATA);
//            if (data.has("error")) {
//                throw new IllegalStateException("Janus streaming error: " + data.path("error").asText());
//            }
//            if (data.has("stream")) {
//                return data;
//            }
//        }
//        throw new IllegalStateException("Timed out waiting for Streaming 'created' event");
//    }
//
//    /* =============================== Builders ================================ */
//
//    private Map<String, Object> mountpointCreateBody(long sessionId, String pin) {
//        var video = new LinkedHashMap<String, Object>();
//        video.put("type", "video");
//        video.put("mid",  "v");                       // обязательно в 1.3.x
//        video.put("port", 0);                         // 0 => Janus сам выделит UDP порт
//        video.put("pt",   96);
//        video.put("codec","h264");
//        video.put("rtpmap","H264/90000");
//        video.put("fmtp", "profile-level-id=42e01f;packetization-mode=1"); // Baseline 3.1
//
//        Map<String, Object> body = new LinkedHashMap<>();
//        body.put("request", "create");
//        body.put("admin_key", adminKey);
//        body.put("type", "rtp");
//        body.put("description", "mp-" + sessionId);
//        body.put("is_private", true);
//        body.put("pin", pin);
//        body.put("permanent", false);
//        body.put("media", List.of(video));
//        return body;
//    }
//
//    private Map<String, String> mountpointCreateCommand() {
//        return Map.of(
//                "janus", "attach",
//                "plugin", "janus.plugin.streaming",
//                "transaction", tx());
//    }
//
//    private static String tx() {
//        return UUID.randomUUID().toString().replace("-", "");
//    }
//
//    private static String sixDigitPin() {
//        return String.valueOf((int)(Math.random() * 900000) + 100000);
//    }
//
//    private static Map<String, Object> janusMessage(Map<String, Object> body) {
//        return Map.of("janus", "message", "body", body, "transaction", tx());
//    }
//
//    /* ================================= DTO ================================== */
//
//    private static class MountpointInfo {
//        final long sessionId;
//        final long handleId;
//        final long mountpointId;
//        final String host;
//        final int port;
//        final String pin;
//        volatile boolean started = false;
//
//        MountpointInfo(long sessionId, long handleId, long mountpointId, String host, int port, String pin) {
//            this.sessionId = sessionId;
//            this.handleId = handleId;
//            this.mountpointId = mountpointId;
//            this.host = host;
//            this.port = port;
//            this.pin = pin;
//        }
//    }
//}
