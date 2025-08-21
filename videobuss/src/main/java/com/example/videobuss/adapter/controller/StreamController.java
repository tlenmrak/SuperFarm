package com.example.videobuss.adapter.controller;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.ResourceUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
<<<<<<< Updated upstream:videobuss/src/main/java/com/example/videobuss/StreamController.java
public class StreamController {
=======
@RequiredArgsConstructor
@Slf4j
public class aStreamController {
>>>>>>> Stashed changes:videobuss/src/main/java/com/example/videobuss/adapter/controller/StreamController.java

    @Value("${video.file}")
    private String videoFile;

    @PostMapping("/start")
    public String start(@RequestBody StreamRequest request) throws IOException {
        File file = ResourceUtils.getFile("classpath:" + videoFile);
        List<String> command = List.of(
            "ffmpeg", "-re", "-i", file.getAbsolutePath(),
            "-c:v", "libx264", "-f", "rtp",
            "rtp://" + request.sfuHost() + ":" + request.sfuPort()
        );
        new ProcessBuilder(command).inheritIO().start();
        return "stream started";
    }

    public record StreamRequest(String sfuHost, int sfuPort) {}
}
