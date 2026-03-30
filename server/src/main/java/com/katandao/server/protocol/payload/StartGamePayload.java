package com.katandao.server.protocol.payload;

public record StartGamePayload(
        String roomId,
        String playerId
) {
}
