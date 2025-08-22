package com.example.signaling.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

@Configuration
public class WsClientConfig {

    @PostConstruct
    public void wsTweaks() {
        // не пытаться ходить через системные прокси на localhost
        System.setProperty("java.net.useSystemProxies", "false");
        System.setProperty("http.proxyHost", "");
        System.setProperty("https.proxyHost", "");
        System.setProperty("socksProxyHost", "");
        // отключить расширения (permessage-deflate) у Tomcat WebSocket-клиента
        System.setProperty("org.apache.tomcat.websocket.DISABLE_BUILTIN_EXTENSIONS", "true");
    }

    @Bean
    public StandardWebSocketClient standardWebSocketClient() {
        return new StandardWebSocketClient();
    }
}
