package com.example.signaling.model.dto;

public record Mountpoint(long sessionId,
                         long handleId,
                         long mountpointId,
                         String host,
                         int port,
                         String pin) {}
