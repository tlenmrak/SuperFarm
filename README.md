# SuperFarm

This repository contains a minimal example for streaming from an iPhone through a Janus SFU.
It includes a Spring Boot signaling service and a tiny browser client that proxies WebRTC
messages through the signaling layer to Janus.

## Janus SFU

Use the provided `docker-compose.yml` to start a Janus instance configured
for this demo. It exposes the WebSocket transport on port `8188` and listens
for RTP on UDP port `5004`.

```bash
docker compose up janus
```

Janus will read its configuration from the files in the `janus/` directory
to enable WebSockets and a streaming mountpoint with ID `1`.

## Signal service

Source code lives in `signal-service`. The service exposes a WebSocket endpoint at `/ws` and
forwards all messages to a Janus server specified via the `janus.url` property (defaults to
`ws://localhost:8188/`). It also notifies the `VideoBuss` service at the
`videobuss.url` with RTP details (`janus.rtp.host` and `janus.rtp.port`) when a
frontend connects so that the video stream can start.


`ws://localhost:8188/`).

To run the service:

```bash
cd signal-service
mvn spring-boot:run
```

## Frontend demo

A very small frontend example is available in the `frontend` directory. It uses the Janus
JavaScript library to request a streaming plugin and display the remote stream.

Serve the static files with any web server, for example:

```bash
npx http-server frontend
```

Then open the page in your browser and the video stream from Janus should appear.

## VideoBuss

The `videobuss` folder now holds a Spring Boot service that simulates a video
source. When the signaling service asks it to start, it reads an MJPEG file and
uses `ffmpeg` to transcode the frames to H.264 before sending them to Janus over
RTP.

Place an MJPEG file at `videobuss/src/main/resources/video.mjpeg` or change the
`video.file` property. Then run the service:

```bash
cd videobuss
mvn spring-boot:run
```

The service listens on port `8081` by default and exposes a `/start` endpoint
that the signaling service calls with RTP connection information. Once active,
the frontend demo will display the streamed video.

