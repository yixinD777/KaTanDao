package com.katandao.server.protocol.payload;

public record SetReadyPayload(
        String roomId,
        String playerId,
        boolean ready
) {
}
