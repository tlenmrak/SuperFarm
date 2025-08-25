package com.example.signaling.config;

import com.example.signaling.adapter.in.ws.JanusProxyHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import static com.example.signaling.constant.Const.ALL;
import static com.example.signaling.constant.Const.WS;
import static com.example.signaling.constant.Const.WS_JANUS;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final JanusProxyHandler handler;

    public WebSocketConfig(JanusProxyHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry
                .addHandler(handler, WS, WS_JANUS)
                .setAllowedOriginPatterns(ALL);
    }
}
