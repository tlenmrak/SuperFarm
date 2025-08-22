package com.example.signaling.usecase;

import com.example.signaling.port.in.CloseConnectionUseCase;
import com.example.signaling.port.out.JanusAdminPort;
import com.example.signaling.port.out.JanusWsPort;
import com.example.signaling.port.core.SessionStatePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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

        // Снести mountpoint (после завершения создания — если ещё создавался)
        state.removeMountpoint(frontendId).ifPresent(fut ->
            fut.whenComplete((mp, ex) -> {
                if (mp == null) return;
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
            })
        );
    }
}
