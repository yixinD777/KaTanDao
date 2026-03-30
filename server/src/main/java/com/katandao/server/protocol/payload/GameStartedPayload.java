package com.katandao.server.protocol.payload;

public record GameStartedPayload(
        String roomId,
        String gameId
) {
}
