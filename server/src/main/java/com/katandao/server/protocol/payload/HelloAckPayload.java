package com.katandao.server.protocol.payload;

public record HelloAckPayload(
        String playerId,
        String sessionId,
        String serverVersion
) {
}
