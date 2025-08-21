package com.example.videobuss;

import java.io.File;
import java.io.IOException;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.ResourceUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequiredArgsConstructor
@Slf4j
public class StreamController {

    public static final String CLASSPATH = "classpath:";
    public static final String FFMPEG = "ffmpeg";
    public static final String IRL_PLAY = "-re";
    public static final String IN_FILE = "-i";
    public static final String RTP = "rtp";
    public static final String FORMAT_OUT = "-f";
    public static final String H_264 = "libx264";
    public static final String CODEC = "-c:v";
    public static final String HOST_RTP = "rtp://";
    @Value("${video.file}")
    private String videoFile;

    private  final ProcessBuilder processBuilder;

    @PostMapping("/start")
    public String start(@RequestBody StreamRequest request) throws IOException {
        var file = ResourceUtils.getFile(CLASSPATH + videoFile);
        processBuilder
                .command(getCommand(request, file))
                .inheritIO()
                .start();

        log.info("Started streaming {} to {}:{}", file.getName(), request.sfuHost(), request.sfuPort());
        return "stream started";
    }

    private List<String> getCommand(StreamRequest request, File file) {
        return List.of(
                FFMPEG, IRL_PLAY, IN_FILE, file.getAbsolutePath(),
                CODEC, H_264, FORMAT_OUT, RTP,
                HOST_RTP + request.sfuHost() + ":" + request.sfuPort()
        );
    }

    public record StreamRequest(String sfuHost, int sfuPort) {}
}
