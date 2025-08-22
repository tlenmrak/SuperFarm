package com.example.videobuss.adapter.controller;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.ResourceUtils;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequiredArgsConstructor
@Slf4j
public class StreamController {

    public static final String NO_PROCESS_FOR_PORT = "no process for port ";
    /** Входной файл (например, "video.mjpeg" в resources) */
    @Value("${video.file}")
    private String videoFile;

    /** Путь к ffmpeg; если он в PATH, можно не задавать (используется "ffmpeg") */
    @Value("${ffmpeg.bin:ffmpeg}")
    private String ffmpegBin;

    /** Активные процессы по RTP-порту Janus */
    private final Map<Integer, Process> running = new ConcurrentHashMap<>();

    @PostMapping("/start")
    public synchronized String start(@RequestBody StreamRequest req) throws IOException {
        if (running.containsKey(req.sfuPort())) {
            return "already streaming to port " + req.sfuPort();
        }

        var in = ResourceUtils.getFile("classpath:" + videoFile);
        var cmd = buildCmd(in, req.sfuHost(), req.sfuPort());
        var logFile = new File("ffmpeg_" + req.sfuPort() + ".log");

        log.info("Starting ffmpeg: {}", String.join(" ", cmd));
        log.info("ffmpeg log: {}", logFile.getAbsolutePath());

        var process = new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))
                .start();
        running.put(req.sfuPort(), process);

        // watcher через Process#onExit
        process.onExit().thenAccept(pr -> {
            int code = pr.exitValue();
            log.info("ffmpeg exited (port {}): {}. See {}", req.sfuPort(), code, logFile.getAbsolutePath());
            running.remove(req.sfuPort(), pr);
        });

        log.info("Started streaming {} to {}:{}", in.getName(), req.sfuHost(), req.sfuPort());
        return "stream started";
    }

    @PostMapping("/stop")
    public synchronized String stop(@RequestBody StreamRequest req) {
        var process = running.remove(req.sfuPort());
        if (process == null)
        {
            return NO_PROCESS_FOR_PORT + req.sfuPort();
        }
        process.destroy();
        try { if (process.isAlive()) process.destroyForcibly(); } catch (Exception ignored) {}
        log.info("Stopped streaming to port {}", req.sfuPort());
        return "stopped";
    }

    @GetMapping("/status")
    public Map<Integer, String> status() {
        var out = new TreeMap<Integer, String>();
        running.forEach((port, proc) -> out.put(port, proc.isAlive() ? "RUNNING" : "EXITED"));
        return out;
    }

    @PreDestroy
    public void shutdown() {
        running.values().forEach(p -> {
            try { p.destroy(); } catch (Exception ignored) {}
            try { if (p.isAlive()) p.destroyForcibly(); } catch (Exception ignored) {}
        });
        running.clear();
    }

    private List<String> buildCmd(File in, String host, int port) {
        return List.of(
                ffmpegBin,
                "-v", "verbose",                // подробные логи в файл
                "-re", "-stream_loop", "-1",
                "-fflags", "+genpts",
                "-r", "25",
                "-i", in.getAbsolutePath(),
                "-an",
                "-c:v", "libx264",
                "-preset", "veryfast",
                "-tune", "zerolatency",
                "-pix_fmt", "yuv420p",
                "-profile:v", "baseline",
                "-level", "3.1",
                "-g", "25", "-keyint_min", "25", "-sc_threshold", "0",
                "-b:v", "1500k", "-maxrate", "1500k", "-bufsize", "1500k",
                "-x264-params", "nal-hrd=cbr:repeat-headers=1",
                "-f", "rtp", "-payload_type", "96",
                String.format("rtp://%s:%d?pkt_size=1200", host, port)
        );
    }

    public record StreamRequest(String sfuHost, int sfuPort) {}
}
