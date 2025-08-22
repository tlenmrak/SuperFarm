package com.example.signaling.adapter.core;

import com.example.signaling.model.dto.Mountpoint;
import com.example.signaling.port.core.SessionStatePort;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

@Component
public class InMemorySessionStateAdapter implements SessionStatePort {

    private final ConcurrentHashMap<String, CompletableFuture<Mountpoint>> mpFutures = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicBoolean> startedFlags = new ConcurrentHashMap<>();

    @Override
    public CompletableFuture<Mountpoint> getOrInitMountpoint(String frontendId,
                                                             Supplier<CompletableFuture<Mountpoint>> factory) {
        return mpFutures.computeIfAbsent(frontendId, id -> {
            if (factory == null) throw new IllegalStateException("Mountpoint not initialized for fid=" + frontendId);
            return factory.get();
        });
    }

    @Override
    public boolean markStartedOnce(String frontendId) {
        return startedFlags.computeIfAbsent(frontendId, k -> new AtomicBoolean(false))
                .compareAndSet(false, true);
    }

    @Override
    public Optional<CompletableFuture<Mountpoint>> removeMountpoint(String frontendId) {
        startedFlags.remove(frontendId);
        return Optional.ofNullable(mpFutures.remove(frontendId));
    }
}
