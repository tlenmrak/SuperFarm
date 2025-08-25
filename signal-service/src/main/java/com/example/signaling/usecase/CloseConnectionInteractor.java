package com.example.signaling.usecase;

import com.example.signaling.model.dto.Mountpoint;
import com.example.signaling.port.in.CloseConnectionUseCase;
import com.example.signaling.port.out.JanusAdminPort;
import com.example.signaling.port.out.JanusWsPort;
import com.example.signaling.port.core.SessionStatePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class CloseConnectionInteractor implements CloseConnectionUseCase {

    private final JanusWsPort janusWs;
    private final SessionStatePort state;
    private final JanusAdminPort janus;

    @Override
    public void close(String frontendId) {
        // Закрыть WS к Janus
        janusWs.close(frontendId);
        state.removeMountpoint(frontendId)
                .ifPresent(this::destroyMounpointWhenComplete);
    }

    private CompletableFuture<Mountpoint> destroyMounpointWhenComplete(CompletableFuture<Mountpoint> mountpointFuture) {
        return mountpointFuture
                .whenComplete((mp, ex) -> destroyMountpoint(mp));
    }

    private void destroyMountpoint(Mountpoint mp) {
        if (mp == null) {
            return;
        }
        try {
            janus.message(mp.sessionId(), mp.handleId(),
                    java.util.Map.of("request", "destroy",
                            "id", mp.mountpointId(),
                            "admin_key", "")  // adminKey может добавлять сам адаптер при желании
            );
            janus.detach(mp.sessionId(), mp.handleId());
            janus.destroySession(mp.sessionId());
            log.info("Destroyed mountpoint {}", mp.mountpointId());
        } catch (Exception e) {
            log.warn("Failed to destroy mountpoint {}", mp.mountpointId(), e);
        }
    }
}
