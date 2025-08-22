package com.example.signaling.port.core;

import com.example.signaling.model.dto.Mountpoint;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public interface SessionStatePort {
    CompletableFuture<Mountpoint> getOrInitMountpoint(String frontendId,
                                                      Supplier<CompletableFuture<Mountpoint>> factory);
    boolean markStartedOnce(String frontendId);
    Optional<CompletableFuture<Mountpoint>> removeMountpoint(String frontendId);
}
