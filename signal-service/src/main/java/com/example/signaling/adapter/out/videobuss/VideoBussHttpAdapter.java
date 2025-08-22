package com.example.signaling.adapter.out.videobuss;

import com.example.signaling.port.out.VideoBussPort;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static com.example.signaling.constant.Const.*;

@Component
@RequiredArgsConstructor
public class VideoBussHttpAdapter implements VideoBussPort {

    private final RestTemplate rest;

    @Value("${videobuss.url}")
    private String base;

    @Override
    public void start(String host, int port) {
        rest.postForObject(base + START, Map.of(SFU_HOST, host, SFU_PORT, port), String.class);
    }
}
