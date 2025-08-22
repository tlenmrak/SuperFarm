package com.example.signaling.port.in;

import com.example.signaling.model.dto.Mountpoint;

import java.util.concurrent.CompletableFuture;

public interface OpenConnectionUseCase {
    CompletableFuture<Mountpoint> open(String frontendId);
}
