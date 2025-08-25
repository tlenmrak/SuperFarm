package com.example.signaling.usecase;

import com.example.signaling.port.core.SessionStatePort;
import com.example.signaling.port.in.ForwardMessageUseCase;
import com.example.signaling.port.out.JanusWsPort;
import com.example.signaling.port.out.VideoBussPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
@RequiredArgsConstructor
@Slf4j
public class ForwardMessageInteractor implements ForwardMessageUseCase {

    private final JanusWsPort janusWs;
    private final SessionStatePort state;
    private final VideoBussPort videoBuss;
    private final PayloadRewriter rewriter;

    @Override
    public void forward(WebSocketSession frontend, String payload) throws Exception {
        final String fid = frontend.getId();

        // 1) обеспечить коннект к Janus (на каждый fid — один канал)
        janusWs.connectIfNeeded(fid, frontend).get();

        // 2) получить/дождаться mountpoint
        var mountpoint = state.getOrInitMountpoint(fid, null).get();

        // 3) запустить VideoBuss ровно один раз
        if (state.markStartedOnce(fid)) {
            videoBuss.start(mountpoint.host(), mountpoint.port());
            log.info("Requested VideoBuss to stream to {}:{}", mountpoint.host(), mountpoint.port());
        }

        // 4) переписать payload (watch.id/pin) и отправить в Janus
        var patched = rewriter.rewriteWatch(payload, mountpoint.mountpointId(), mountpoint.pin());
        janusWs.send(fid, patched);
    }
}
