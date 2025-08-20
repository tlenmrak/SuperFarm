# SuperFarm

This repository contains a minimal example for streaming from an iPhone through a Janus SFU.
It includes a Spring Boot signaling service and a tiny browser client that proxies WebRTC
messages through the signaling layer to Janus.

## Signal service

Source code lives in `signal-service`. The service exposes a WebSocket endpoint at `/ws` and
forwards all messages to a Janus server specified via the `janus.url` property (defaults to
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
